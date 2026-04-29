#!/usr/bin/env python3
"""Emit an MBR partition table for the Superbird's eMMC user area.

The Spotify Car Thing's eMMC carries an Amlogic Master Partition
Table (AML MPT) that u-boot's amlmmc commands consume. Mainline
Linux can't parse the AML MPT, but the MBR partition-table region
at sector 0 byte 0x1BE..0x1FF is otherwise unused - overlaying an
MBR there gives Linux a partition view of the same eMMC without
disturbing u-boot's view.

The partition table is parameterized so dev and prod images can
ship different geometries. Defaults match the stock AML MPT exactly
(production layout) so a dev image can override per-recipe via
the SUPERBIRD_PART_TABLE bbclass var.

Two output forms:
  * Default - emit just the 66 bytes (4 partition entries + 0x55 0xAA).
  * --in-place <bootloader.dump> - overlay the MBR onto the
    bootloader image directly. This is the one we use in the
    flashthing flow so writeLargeMemory of bootloader.dump lands
    the MBR at user-area sector 0 along with BL2 + FIP.

Default layout (matches the stock AML MPT byte-for-byte):
    p1  system_a  start=0x10600000  size=0x2040b000  (516 MB)
    p2  system_b  start=0x3120b000  size=0x2040b000  (516 MB)
    p3  settings  start=0x52e16000  size=0x10000000  (256 MB)
    p4  data      start=0x63616000  size=0x859ea000  (~2 GB)
"""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

SECTOR = 512
PT_OFFSET = 0x1BE
MBR_MAGIC = b"\x55\xaa"

# Each entry is 16 bytes:
#   status(1) chs_first(3) type(1) chs_last(3) lba_start(4) lba_size(4)
ENTRY_FMT = "<BBBBBBBBII"
TYPE_LINUX = 0x83

DEFAULT_TABLE = (
    "system_a:0x10600000:0x2040b000,"
    "system_b:0x3120b000:0x2040b000,"
    "settings:0x52e16000:0x10000000,"
    "data:0x63616000:0x859ea000"
)


def to_lba(byte_offset: int) -> int:
    if byte_offset % SECTOR:
        raise ValueError(f"offset {byte_offset:#x} not sector-aligned")
    return byte_offset // SECTOR


def entry(start_bytes: int, size_bytes: int, ptype: int = TYPE_LINUX) -> bytes:
    return struct.pack(
        ENTRY_FMT,
        0x00,  # status (not bootable)
        0xFE,
        0xFF,
        0xFF,  # CHS first (legacy, ignored)
        ptype,
        0xFE,
        0xFF,
        0xFF,  # CHS last
        to_lba(start_bytes) & 0xFFFFFFFF,
        to_lba(size_bytes) & 0xFFFFFFFF,
    )


def parse_table(table_str: str) -> list[tuple[str, int, int]]:
    parts: list[tuple[str, int, int]] = []
    for spec in table_str.split(","):
        spec = spec.strip()
        if not spec:
            continue
        fields = spec.split(":")
        if len(fields) != 3:
            raise ValueError(f"bad partition spec {spec!r}; expected name:start:size")
        name, start_s, size_s = fields
        parts.append((name, int(start_s, 0), int(size_s, 0)))
    if not parts:
        raise ValueError("empty partition table")
    if len(parts) > 4:
        raise ValueError(
            f"MBR has 4 primary partition slots; got {len(parts)}: "
            + ", ".join(p[0] for p in parts)
        )
    sorted_parts = sorted(parts, key=lambda p: p[1])
    for a, b in zip(sorted_parts, sorted_parts[1:]):
        a_end = a[1] + a[2]
        if a_end > b[1]:
            raise ValueError(
                f"partitions overlap: {a[0]} ends at {a_end:#x} "
                f"but {b[0]} starts at {b[1]:#x}"
            )
    return parts


def build_table(parts: list[tuple[str, int, int]]) -> bytes:
    entries = b"".join(entry(start, size) for _, start, size in parts)
    pad = b"\x00" * (16 * (4 - len(parts)))
    return entries + pad + MBR_MAGIC


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--output", type=Path, help="emit a 66-byte standalone MBR blob")
    g.add_argument(
        "--in-place",
        type=Path,
        help="overlay MBR onto an existing file at offset 0x1BE",
    )
    p.add_argument(
        "--table",
        default=DEFAULT_TABLE,
        help="comma-separated name:start:size partition specs",
    )
    args = p.parse_args(argv[1:])

    parts = parse_table(args.table)
    table = build_table(parts)

    if args.output:
        args.output.write_bytes(table)
        print(f"wrote 66-byte MBR table to {args.output}")
    else:
        with args.in_place.open("r+b") as f:
            f.seek(PT_OFFSET)
            f.write(table)
        print(f"overlaid MBR onto {args.in_place} at offset {PT_OFFSET:#x}")

    for name, start, size in parts:
        print(
            f"  {name:10s} start={start:#11x} size={size:#11x} "
            f"lba_start={to_lba(start)} lba_count={to_lba(size)}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
