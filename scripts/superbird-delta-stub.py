#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""HTTP-Range stub for swupdate's delta handler benchtop testing.

Stands in for the bridgething daemon's localhost HTTP bridge plus the
gateway-side .zck cache. Run on the host machine, bind to 10.42.1.1:8000
(host side of the USB-gadget link), serve the .zck artifacts produced
by the prod image build. The Superbird's swupdate delta handler points
at http://10.42.1.1:8000/<file>.zck and gets proper RFC 7233 range
responses back.

Usage:
    superbird-delta-stub.py <dir> [--addr 10.42.1.1] [--port 8000]

The serve dir should contain `system.ext2.zck` and any other artifacts
referenced by the .swu's delta handler `url` properties.

Differences vs. the production bridgething daemon path:
- Bound to USB-gadget host side, not localhost
- No translation to RFCOMM — just reads from disk
- No gateway-side cache eviction logic

Both paths serve the same wire protocol (HTTP/1.1 + Range), so swupdate
sees an identical response shape.
"""
import argparse
import logging
import os
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = logging.getLogger("delta-stub")


class RangeHandler(BaseHTTPRequestHandler):
    server_version = "superbird-delta-stub/1"
    serve_root: str = ""

    def log_message(self, fmt, *args):
        LOG.info("%s - %s", self.address_string(), fmt % args)

    def _resolve(self) -> str | None:
        rel = self.path.lstrip("/").split("?", 1)[0]
        # Reject path traversal and absolute paths at the URL level.
        if not rel or rel.startswith("..") or "/.." in rel or rel.startswith("/"):
            self.send_error(400, "bad path")
            return None
        # Textual normpath only - don't realpath. The bridgething-ota
        # flow stages a tmp serve dir with system.img.zck symlinked at
        # the build's deploy artifact (which lives outside the serve
        # dir); realpath-ing through that link would falsely flag it
        # as escape. The URL-level check above is what blocks actual
        # `..` traversal.
        root = os.path.realpath(self.serve_root)
        full = os.path.normpath(os.path.join(root, rel))
        if full != root and not full.startswith(root + os.sep):
            self.send_error(403, "outside serve root")
            return None
        if not os.path.isfile(full):
            self.send_error(404, "not found")
            return None
        return full

    def do_HEAD(self):
        path = self._resolve()
        if path is None:
            return
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Length", str(size))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Type", "application/octet-stream")
        self.end_headers()

    def do_GET(self):
        path = self._resolve()
        if path is None:
            return
        size = os.path.getsize(path)
        rng = self.headers.get("Range")

        if rng is None:
            self.send_response(200)
            self.send_header("Content-Length", str(size))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Type", "application/octet-stream")
            self.end_headers()
            with open(path, "rb") as f:
                while chunk := f.read(64 * 1024):
                    self.wfile.write(chunk)
            return

        # Parse `Range: bytes=A-B[, C-D, ...]`. swupdate's delta
        # downloader batches up to ~150 ranges per request by default,
        # so multipart ranges must work.
        m = re.match(r"^bytes=(.+)$", rng.strip())
        if not m:
            self.send_error(400, f"bad Range: {rng!r}")
            return
        spec = m.group(1)
        ranges = []
        for part in spec.split(","):
            part = part.strip()
            mr = re.match(r"^(\d*)-(\d*)$", part)
            if not mr or (mr.group(1) == "" and mr.group(2) == ""):
                self.send_error(400, f"bad range part: {part!r}")
                return
            a_s, b_s = mr.group(1), mr.group(2)
            if a_s == "":
                # suffix range: last N bytes
                length = int(b_s)
                start = max(0, size - length)
                end = size - 1
            elif b_s == "":
                start, end = int(a_s), size - 1
            else:
                start, end = int(a_s), int(b_s)
            if start > end or start >= size:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{size}")
                self.end_headers()
                return
            end = min(end, size - 1)
            ranges.append((start, end))

        if len(ranges) == 1:
            start, end = ranges[0]
            length = end - start + 1
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.send_header("Content-Length", str(length))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Type", "application/octet-stream")
            self.end_headers()
            with open(path, "rb") as f:
                f.seek(start)
                remaining = length
                while remaining > 0:
                    chunk = f.read(min(64 * 1024, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)
            return

        # Multipart byteranges. Build the body in memory — fine for
        # zchunk's request shape (each part is just a few KB of header
        # metadata or content-defined chunk).
        boundary = "superbird-delta-stub-boundary"
        body_parts = []
        for start, end in ranges:
            with open(path, "rb") as f:
                f.seek(start)
                data = f.read(end - start + 1)
            header = (
                f"\r\n--{boundary}\r\n"
                f"Content-Type: application/octet-stream\r\n"
                f"Content-Range: bytes {start}-{end}/{size}\r\n\r\n"
            ).encode("ascii")
            body_parts.append(header + data)
        body_parts.append(f"\r\n--{boundary}--\r\n".encode("ascii"))
        body = b"".join(body_parts)
        self.send_response(206)
        self.send_header(
            "Content-Type", f"multipart/byteranges; boundary={boundary}"
        )
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("serve_dir", help="dir holding .zck artifacts")
    p.add_argument("--addr", default="10.42.1.1",
                   help="bind address (default 10.42.1.1, USB-gadget host side)")
    p.add_argument("--port", type=int, default=8000)
    p.add_argument("--verbose", "-v", action="store_true")
    args = p.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    if not os.path.isdir(args.serve_dir):
        print(f"not a directory: {args.serve_dir}", file=sys.stderr)
        return 1

    RangeHandler.serve_root = os.path.realpath(args.serve_dir)
    server = ThreadingHTTPServer((args.addr, args.port), RangeHandler)
    LOG.info("serving %s at http://%s:%d/", RangeHandler.serve_root,
             args.addr, args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        LOG.info("shutting down")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
