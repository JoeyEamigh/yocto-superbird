#!/bin/sh
# first-boot: carve the unallocated eMMC tail as the data partition, then
# hot-add the new partition to the running kernel and settle udev so the
# fstab generator's var.mount picks it up without a reboot.
set -e

DISK=/dev/mmcblk0

REQUIRE_HEADROOM=@@REQUIRE_HEADROOM@@
BOOT_PART_MIB=@@BOOT_PART_MIB@@
ROOT_PART_MIB=@@ROOT_PART_MIB@@
MARGIN_MIB=@@MARGIN_MIB@@

if sgdisk -p "$DISK" 2>/dev/null | grep -qw data; then
    exit 0
fi

if [ "$REQUIRE_HEADROOM" = "1" ]; then
    disk_sectors=$(blockdev --getsz "$DISK" 2>/dev/null || echo 0)
    last_alloc=$(sgdisk -p "$DISK" 2>/dev/null | awk '/^ +[0-9]+ +/ { print $3 }' | sort -n | tail -1)
    last_alloc=${last_alloc:-0}
    free_sectors=$((disk_sectors - last_alloc - 34))
    [ "$free_sectors" -lt 0 ] && free_sectors=0
    free_mib=$((free_sectors / 2048))
    required=$((BOOT_PART_MIB + ROOT_PART_MIB + MARGIN_MIB))
    if [ "$free_mib" -lt "$required" ]; then
        echo "superbird-provision: only ${free_mib} MiB free, need ${required} MiB for an ota write" >&2
        exit 1
    fi
fi

echo "superbird-provision: carving data on $DISK"
sgdisk -e -n 0:0:0 -c 0:data -t 0:8300 "$DISK"
sync

data_num=$(sgdisk -p "$DISK" 2>/dev/null | awk '$NF == "data" { print $1 }')
if [ -z "$data_num" ]; then
    echo "superbird-provision: data partition number not found after sgdisk" >&2
    exit 1
fi
# partx -a prints "error adding partition N" even on success.
partx -a -n "$data_num" "$DISK" 2>&1 | grep -v "error adding partition" >&2 || true

udevadm settle
