#!/usr/bin/env python3
"""Pack an Android boot.img v0 (kernel + optional dtb in second-stage).

Targets the legacy v0 header that stock Amlogic u-boot's `imgread kernel`
+ `bootm` chain consumes. Page size and load addresses are pulled from a
stock boot_a.dump on the Spotify Car Thing (S905D2) so the partition
matches what the stock loader expects.

Usage:
    mkbootimg --kernel Image.gz --dtb meson-g12a-superbird.dtb -o boot.img

Defaults match Car Thing stock; flags exist if a different SoC needs
overriding.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
from pathlib import Path

MAGIC = b"ANDROID!"
HEADER_SIZE_PADDED = 1632  # struct boot_img_hdr v0
HEADER_VERSION = 0


def page_round(n: int, page: int) -> int:
    if n == 0:
        return 0
    return ((n + page - 1) // page) * page


def build_v0(
    kernel: bytes,
    dtb: bytes | None,
    *,
    kernel_addr: int,
    ramdisk_addr: int,
    second_addr: int,
    tags_addr: int,
    page_size: int,
    name: str,
    cmdline: str,
) -> bytes:
    ramdisk = b""
    second = dtb or b""

    # Header layout matches AOSP boot_img_hdr_v0 exactly.
    hdr = bytearray(HEADER_SIZE_PADDED)
    struct.pack_into("<8s", hdr, 0, MAGIC)
    struct.pack_into("<I", hdr, 0x08, len(kernel))
    struct.pack_into("<I", hdr, 0x0C, kernel_addr)
    struct.pack_into("<I", hdr, 0x10, len(ramdisk))
    struct.pack_into("<I", hdr, 0x14, ramdisk_addr)
    struct.pack_into("<I", hdr, 0x18, len(second))
    struct.pack_into("<I", hdr, 0x1C, second_addr)
    struct.pack_into("<I", hdr, 0x20, tags_addr)
    struct.pack_into("<I", hdr, 0x24, page_size)
    struct.pack_into("<I", hdr, 0x28, HEADER_VERSION)
    struct.pack_into("<I", hdr, 0x2C, 0)  # os_version
    name_bytes = name.encode("utf-8")[:16].ljust(16, b"\0")
    struct.pack_into("<16s", hdr, 0x30, name_bytes)
    cmdline_bytes = cmdline.encode("utf-8")[:512].ljust(512, b"\0")
    struct.pack_into("<512s", hdr, 0x40, cmdline_bytes)
    # id[8]: SHA-1 of kernel || kernel_size || ramdisk || ramdisk_size
    #        || second || second_size - AOSP's exact recipe.
    h = hashlib.sha1()
    h.update(kernel)
    h.update(struct.pack("<I", len(kernel)))
    h.update(ramdisk)
    h.update(struct.pack("<I", len(ramdisk)))
    h.update(second)
    h.update(struct.pack("<I", len(second)))
    digest = h.digest() + b"\0" * 12  # pad to 32 bytes
    struct.pack_into("<32s", hdr, 0x240, digest)
    # extra_cmdline left zero (we use plain cmdline).

    out = bytearray()
    out += hdr.ljust(page_size, b"\0")
    out += kernel.ljust(page_round(len(kernel), page_size), b"\0")
    if ramdisk:
        out += ramdisk.ljust(page_round(len(ramdisk), page_size), b"\0")
    if second:
        out += second.ljust(page_round(len(second), page_size), b"\0")
    return bytes(out)


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--kernel", required=True, type=Path)
    p.add_argument(
        "--dtb",
        type=Path,
        default=None,
        help="device tree, packed in the second-stage slot",
    )
    p.add_argument("-o", "--output", required=True, type=Path)
    p.add_argument("--kernel-addr", type=lambda s: int(s, 0), default=0x01080000)
    p.add_argument("--ramdisk-addr", type=lambda s: int(s, 0), default=0x01000000)
    p.add_argument("--second-addr", type=lambda s: int(s, 0), default=0x00F00000)
    p.add_argument("--tags-addr", type=lambda s: int(s, 0), default=0x00000100)
    p.add_argument("--page-size", type=int, default=2048)
    p.add_argument("--name", default="bridgething")
    p.add_argument(
        "--cmdline",
        default="",
        help="kernel cmdline; left empty when u-boot env builds bootargs",
    )
    args = p.parse_args()

    kernel = args.kernel.read_bytes()
    dtb = args.dtb.read_bytes() if args.dtb else None

    img = build_v0(
        kernel,
        dtb,
        kernel_addr=args.kernel_addr,
        ramdisk_addr=args.ramdisk_addr,
        second_addr=args.second_addr,
        tags_addr=args.tags_addr,
        page_size=args.page_size,
        name=args.name,
        cmdline=args.cmdline,
    )
    args.output.write_bytes(img)
    print(
        f"{args.output} ({len(img)} bytes, kernel={len(kernel)}, "
        f"dtb={len(dtb) if dtb else 0})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
