//! Reverse proxy that makes chromium's DevTools endpoint reachable by hostname and over adb.

use std::env;
use std::io::{self, Read, Write};
use std::net::{IpAddr, Shutdown, TcpListener, TcpStream};
use std::os::linux::net::SocketAddrExt;
use std::os::unix::net::{SocketAddr as UnixSocketAddr, UnixListener, UnixStream};
use std::thread;
use std::time::Duration;

const DEFAULT_LISTEN: &str = "0.0.0.0:9222";
const DEFAULT_TARGET: &str = "127.0.0.1:9223";
const DEFAULT_ABSTRACT: &str = "chrome_devtools_remote";

const MAX_HEAD: usize = 32 * 1024;
const HEAD_READ_TIMEOUT: Duration = Duration::from_secs(30);

fn main() {
    let listen = env::var("KIOSK_CDP_PROXY_LISTEN").unwrap_or_else(|_| DEFAULT_LISTEN.to_string());
    let target = env::var("KIOSK_CDP_PROXY_TARGET").unwrap_or_else(|_| DEFAULT_TARGET.to_string());
    let abstract_name =
        env::var("KIOSK_CDP_PROXY_ABSTRACT").unwrap_or_else(|_| DEFAULT_ABSTRACT.to_string());

    let listener = match TcpListener::bind(&listen) {
        Ok(l) => l,
        Err(err) => {
            eprintln!("kiosk-cdp-proxy: cannot bind {listen}: {err}");
            std::process::exit(1);
        }
    };

    let advertise_port = match listener.local_addr() {
        Ok(addr) => addr.port(),
        Err(err) => {
            eprintln!("kiosk-cdp-proxy: cannot read listen address: {err}");
            std::process::exit(1);
        }
    };

    println!("kiosk-cdp-proxy: {listen} -> {target}");

    if !abstract_name.is_empty() {
        spawn_abstract_listener(&abstract_name, &target);
    }

    for incoming in listener.incoming() {
        match incoming {
            Ok(client) => {
                let target = target.clone();
                let policy = match client.local_addr() {
                    Ok(addr) => HostPolicy::Fixed(authority(addr.ip(), advertise_port)),
                    Err(err) => {
                        eprintln!("kiosk-cdp-proxy: no local address for client: {err}");
                        continue;
                    }
                };
                thread::spawn(move || {
                    if let Err(err) = serve(client, &policy, &target) {
                        eprintln!("kiosk-cdp-proxy: connection closed: {err}");
                    }
                });
            }
            Err(err) => eprintln!("kiosk-cdp-proxy: accept failed: {err}"),
        }
    }
}

enum HostPolicy {
    Fixed(String),
    PreserveIfServable(String),
}

impl HostPolicy {
    fn resolve(&self, head: &str) -> String {
        match self {
            HostPolicy::Fixed(value) => value.clone(),
            HostPolicy::PreserveIfServable(fallback) => match extract_host(head) {
                Some(host) if is_servable_authority(host) => host.to_string(),
                _ => fallback.clone(),
            },
        }
    }
}

fn extract_host(head: &str) -> Option<&str> {
    head.split("\r\n").skip(1).find_map(|line| {
        let (name, value) = line.split_once(':')?;
        name.eq_ignore_ascii_case("host")
            .then(|| value.trim())
            .filter(|value| !value.is_empty())
    })
}

fn is_servable_authority(authority: &str) -> bool {
    let host = match authority.strip_prefix('[') {
        Some(rest) => match rest.split_once(']') {
            Some((inner, _)) => inner,
            None => return false,
        },
        None => authority.rsplit_once(':').map_or(authority, |(h, _)| h),
    };

    if host.parse::<IpAddr>().is_ok() {
        return true;
    }

    let lower = host.to_ascii_lowercase();
    lower == "localhost" || lower.ends_with(".localhost")
}

fn spawn_abstract_listener(name: &str, target: &str) {
    let addr = match UnixSocketAddr::from_abstract_name(name.as_bytes()) {
        Ok(addr) => addr,
        Err(err) => {
            eprintln!("kiosk-cdp-proxy: bad abstract name {name}: {err}");
            return;
        }
    };

    let listener = match UnixListener::bind_addr(&addr) {
        Ok(l) => l,
        Err(err) => {
            eprintln!("kiosk-cdp-proxy: cannot bind @{name}: {err}");
            return;
        }
    };

    println!("kiosk-cdp-proxy: @{name} -> {target}");

    let target = target.to_string();
    thread::spawn(move || {
        for incoming in listener.incoming() {
            match incoming {
                Ok(client) => {
                    let target = target.clone();
                    let policy = HostPolicy::PreserveIfServable(target.clone());
                    thread::spawn(move || {
                        if let Err(err) = serve(client, &policy, &target) {
                            eprintln!("kiosk-cdp-proxy: abstract connection closed: {err}");
                        }
                    });
                }
                Err(err) => eprintln!("kiosk-cdp-proxy: abstract accept failed: {err}"),
            }
        }
    });
}

trait Duplex: Read + Write + Send + Sized + 'static {
    fn try_clone_duplex(&self) -> io::Result<Self>;
    fn shutdown_write(&self) -> io::Result<()>;
    fn set_read_timeout_duplex(&self, timeout: Option<Duration>) -> io::Result<()>;
}

impl Duplex for TcpStream {
    fn try_clone_duplex(&self) -> io::Result<Self> {
        self.try_clone()
    }
    fn shutdown_write(&self) -> io::Result<()> {
        self.shutdown(Shutdown::Write)
    }
    fn set_read_timeout_duplex(&self, timeout: Option<Duration>) -> io::Result<()> {
        self.set_read_timeout(timeout)
    }
}

impl Duplex for UnixStream {
    fn try_clone_duplex(&self) -> io::Result<Self> {
        self.try_clone()
    }
    fn shutdown_write(&self) -> io::Result<()> {
        self.shutdown(Shutdown::Write)
    }
    fn set_read_timeout_duplex(&self, timeout: Option<Duration>) -> io::Result<()> {
        self.set_read_timeout(timeout)
    }
}

fn serve<S: Duplex>(mut client: S, policy: &HostPolicy, target: &str) -> io::Result<()> {
    client.set_read_timeout_duplex(Some(HEAD_READ_TIMEOUT))?;
    let (head, body_start) = read_head(&mut client)?;
    client.set_read_timeout_duplex(None)?;

    let rewritten = rewrite_head(&head, &policy.resolve(&head));

    let mut upstream = TcpStream::connect(target)?;
    upstream.write_all(rewritten.as_bytes())?;
    if !body_start.is_empty() {
        upstream.write_all(&body_start)?;
    }
    upstream.flush()?;

    let mut client_read = client.try_clone_duplex()?;
    let mut upstream_write = upstream.try_clone()?;
    let pump = thread::spawn(move || {
        let _ = io::copy(&mut client_read, &mut upstream_write);
        let _ = upstream_write.shutdown(Shutdown::Write);
    });

    let _ = io::copy(&mut upstream, &mut client);
    let _ = client.shutdown_write();
    let _ = pump.join();
    Ok(())
}

fn authority(ip: IpAddr, port: u16) -> String {
    match ip {
        IpAddr::V4(v4) => format!("{v4}:{port}"),
        IpAddr::V6(v6) => format!("[{v6}]:{port}"),
    }
}

fn read_head<S: Read>(client: &mut S) -> io::Result<(String, Vec<u8>)> {
    let mut buf = Vec::with_capacity(1024);
    let mut chunk = [0u8; 1024];

    loop {
        if let Some(end) = find_head_end(&buf) {
            let body = buf.split_off(end);
            let head = String::from_utf8_lossy(&buf).into_owned();
            return Ok((head, body));
        }

        if buf.len() > MAX_HEAD {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "request head too large",
            ));
        }

        let read = client.read(&mut chunk)?;
        if read == 0 {
            return Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "client closed before end of head",
            ));
        }
        buf.extend_from_slice(&chunk[..read]);
    }
}

fn find_head_end(buf: &[u8]) -> Option<usize> {
    buf.windows(4).position(|w| w == b"\r\n\r\n").map(|i| i + 4)
}

fn rewrite_head(head: &str, host_value: &str) -> String {
    let is_upgrade = head
        .lines()
        .any(|line| line.to_ascii_lowercase().starts_with("upgrade:"));

    let mut out = String::with_capacity(head.len() + 64);
    let mut host_written = false;

    for (index, line) in head.split("\r\n").enumerate() {
        if index == 0 {
            out.push_str(line);
            out.push_str("\r\n");
            continue;
        }

        if line.is_empty() {
            break;
        }

        let lower = line.to_ascii_lowercase();
        if lower.starts_with("host:") {
            out.push_str("Host: ");
            out.push_str(host_value);
            out.push_str("\r\n");
            host_written = true;
            continue;
        }
        if !is_upgrade && (lower.starts_with("connection:") || lower.starts_with("keep-alive:")) {
            continue;
        }

        out.push_str(line);
        out.push_str("\r\n");
    }

    if !host_written {
        out.push_str("Host: ");
        out.push_str(host_value);
        out.push_str("\r\n");
    }
    if !is_upgrade {
        out.push_str("Connection: close\r\n");
    }

    out.push_str("\r\n");
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::{Ipv4Addr, Ipv6Addr};

    #[test]
    fn rewrites_host_and_closes_plain_requests() {
        let head =
            "GET /json/list HTTP/1.1\r\nHost: device.local:9222\r\nConnection: keep-alive\r\n\r\n";
        let out = rewrite_head(head, "10.0.0.5:9222");
        assert!(out.contains("Host: 10.0.0.5:9222\r\n"));
        assert!(!out.contains("device.local"));
        assert!(out.contains("Connection: close\r\n"));
        assert!(!out.contains("keep-alive"));
    }

    #[test]
    fn preserves_connection_headers_on_upgrade() {
        let head = "GET /devtools/page/A HTTP/1.1\r\nHost: device.local:9222\r\n\
                    Connection: Upgrade\r\nUpgrade: websocket\r\n\r\n";
        let out = rewrite_head(head, "10.0.0.5:9222");
        assert!(out.contains("Host: 10.0.0.5:9222\r\n"));
        assert!(out.contains("Connection: Upgrade\r\n"));
        assert!(out.contains("Upgrade: websocket\r\n"));
        assert!(!out.contains("Connection: close"));
    }

    #[test]
    fn adds_host_when_request_omits_it() {
        let head = "GET /json/version HTTP/1.1\r\n\r\n";
        let out = rewrite_head(head, "10.0.0.5:9222");
        assert!(out.contains("Host: 10.0.0.5:9222\r\n"));
    }

    #[test]
    fn matches_headers_case_insensitively() {
        let head = "GET / HTTP/1.1\r\nhOsT: device.local:9222\r\n\r\n";
        let out = rewrite_head(head, "10.0.0.5:9222");
        assert_eq!(out.matches("10.0.0.5:9222").count(), 1);
        assert!(!out.contains("device.local"));
    }

    #[test]
    fn brackets_ipv6_authority() {
        assert_eq!(
            authority(IpAddr::V4(Ipv4Addr::new(10, 0, 0, 5)), 9222),
            "10.0.0.5:9222"
        );
        assert_eq!(
            authority(IpAddr::V6(Ipv6Addr::LOCALHOST), 9222),
            "[::1]:9222"
        );
    }

    #[test]
    fn finds_head_boundary_and_keeps_body() {
        let raw = b"GET / HTTP/1.1\r\nHost: x\r\n\r\nBODY";
        let end = find_head_end(raw).expect("boundary");
        assert_eq!(&raw[end..], b"BODY");
    }

    #[test]
    fn default_abstract_name_is_discoverable() {
        assert!(DEFAULT_ABSTRACT.contains("_devtools_remote"));
    }

    #[test]
    fn recognises_authorities_chromium_will_serve() {
        for ok in [
            "127.0.0.1:9999",
            "10.42.1.114",
            "localhost:9222",
            "LOCALHOST",
            "foo.localhost:1",
            "[::1]:9222",
        ] {
            assert!(is_servable_authority(ok), "should accept {ok}");
        }
        for bad in ["bridgething.local:9222", "example.com", "bogus", "[::1", ""] {
            assert!(!is_servable_authority(bad), "should reject {bad}");
        }
    }

    #[test]
    fn extracts_host_case_insensitively_and_skips_request_line() {
        let head = "GET /json HTTP/1.1\r\nX-Host: nope\r\nhOsT:  127.0.0.1:9999 \r\n\r\n";
        assert_eq!(extract_host(head), Some("127.0.0.1:9999"));
        assert_eq!(extract_host("GET /json HTTP/1.1\r\n\r\n"), None);
        assert_eq!(extract_host("GET /json HTTP/1.1\r\nHost:   \r\n\r\n"), None);
    }

    #[test]
    fn abstract_policy_preserves_forwarded_port_but_replaces_unservable() {
        let policy = HostPolicy::PreserveIfServable("127.0.0.1:9223".to_string());
        let forwarded = "GET /json HTTP/1.1\r\nHost: 127.0.0.1:9999\r\n\r\n";
        assert_eq!(policy.resolve(forwarded), "127.0.0.1:9999");

        let named = "GET /json HTTP/1.1\r\nHost: bridgething.local:9222\r\n\r\n";
        assert_eq!(policy.resolve(named), "127.0.0.1:9223");

        let hostless = "GET /json HTTP/1.1\r\n\r\n";
        assert_eq!(policy.resolve(hostless), "127.0.0.1:9223");
    }

    #[test]
    fn tcp_policy_always_uses_the_interface_authority() {
        let policy = HostPolicy::Fixed("10.42.1.114:9222".to_string());
        let head = "GET /json HTTP/1.1\r\nHost: 127.0.0.1:9999\r\n\r\n";
        assert_eq!(policy.resolve(head), "10.42.1.114:9222");
    }

    #[test]
    fn abstract_socket_binds_and_relays_host_header() {
        let upstream = TcpListener::bind("127.0.0.1:0").expect("upstream");
        let target = upstream.local_addr().expect("addr").to_string();

        let name = format!("kiosk_cdp_test_{}_devtools_remote", std::process::id());
        spawn_abstract_listener(&name, &target);

        let addr = UnixSocketAddr::from_abstract_name(name.as_bytes()).expect("addr");
        let mut client = UnixStream::connect_addr(&addr).expect("connect");
        client
            .write_all(b"GET /json/list HTTP/1.1\r\nHost: bogus\r\n\r\n")
            .expect("write");

        let (mut server, _) = upstream.accept().expect("accept");
        let mut buf = [0u8; 256];
        let n = server.read(&mut buf).expect("read");
        let got = String::from_utf8_lossy(&buf[..n]).into_owned();

        assert!(got.contains(&format!("Host: {target}\r\n")), "got: {got}");
        assert!(!got.contains("bogus"));
    }
}
