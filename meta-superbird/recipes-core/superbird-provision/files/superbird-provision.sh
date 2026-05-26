#!/bin/sh
# first-boot: carve the unallocated eMMC tail as the data partition + reboot.
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
    free_sectors=$(sgdisk -p "$DISK" 2>/dev/null | awk -F: '/Total free space is/ { gsub(/[^0-9]/, "", $2); print $2 }')
    if [ -z "$free_sectors" ]; then
        end=$(sgdisk -p "$DISK" 2>/dev/null | awk '/last usable sector is/ { print $NF }')
        first=$(sgdisk -p "$DISK" 2>/dev/null | awk '/first usable sector is/ { print $NF }')
        last_alloc=$(sgdisk -p "$DISK" 2>/dev/null | awk '/^ +[0-9]+ +/ { print $3 }' | sort -n | tail -1)
        if [ -n "$end" ] && [ -n "$first" ] && [ -n "$last_alloc" ]; then
            free_sectors=$((end - last_alloc))
        else
            free_sectors=0
        fi
    fi
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
systemctl reboot
