//! Reverse proxy that makes chromium's DevTools endpoint reachable by hostname.

use std::env;
use std::io::{self, Read, Write};
use std::net::{IpAddr, Shutdown, TcpListener, TcpStream};
use std::thread;
use std::time::Duration;

const DEFAULT_LISTEN: &str = "0.0.0.0:9222";
const DEFAULT_TARGET: &str = "127.0.0.1:9223";

const MAX_HEAD: usize = 32 * 1024;
const HEAD_READ_TIMEOUT: Duration = Duration::from_secs(30);

fn main() {
    let listen = env::var("KIOSK_CDP_PROXY_LISTEN").unwrap_or_else(|_| DEFAULT_LISTEN.to_string());
    let target = env::var("KIOSK_CDP_PROXY_TARGET").unwrap_or_else(|_| DEFAULT_TARGET.to_string());

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

    for incoming in listener.incoming() {
        match incoming {
            Ok(client) => {
                let target = target.clone();
                thread::spawn(move || {
                    if let Err(err) = handle(client, &target, advertise_port) {
                        eprintln!("kiosk-cdp-proxy: connection closed: {err}");
                    }
                });
            }
            Err(err) => eprintln!("kiosk-cdp-proxy: accept failed: {err}"),
        }
    }
}

fn handle(mut client: TcpStream, target: &str, advertise_port: u16) -> io::Result<()> {
    let host_value = authority(client.local_addr()?.ip(), advertise_port);

    client.set_read_timeout(Some(HEAD_READ_TIMEOUT))?;
    let (head, body_start) = read_head(&mut client)?;
    client.set_read_timeout(None)?;

    let rewritten = rewrite_head(&head, &host_value);

    let mut upstream = TcpStream::connect(target)?;
    upstream.write_all(rewritten.as_bytes())?;
    if !body_start.is_empty() {
        upstream.write_all(&body_start)?;
    }
    upstream.flush()?;

    let mut client_read = client.try_clone()?;
    let mut upstream_write = upstream.try_clone()?;
    let pump = thread::spawn(move || {
        let _ = io::copy(&mut client_read, &mut upstream_write);
        let _ = upstream_write.shutdown(Shutdown::Write);
    });

    let _ = io::copy(&mut upstream, &mut client);
    let _ = client.shutdown(Shutdown::Write);
    let _ = pump.join();
    Ok(())
}

fn authority(ip: IpAddr, port: u16) -> String {
    match ip {
        IpAddr::V4(v4) => format!("{v4}:{port}"),
        IpAddr::V6(v6) => format!("[{v6}]:{port}"),
    }
}

fn read_head(client: &mut TcpStream) -> io::Result<(String, Vec<u8>)> {
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
}
