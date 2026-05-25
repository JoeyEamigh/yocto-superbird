#!/bin/sh
# first-boot: carve the unallocated eMMC tail as the ext4 data partition + reboot.
# sgdisk -e first relocates the backup GPT to the true end of disk.
set -e

DISK=/dev/mmcblk0

if sgdisk -p "$DISK" 2>/dev/null | grep -qw data; then
    exit 0
fi

echo "superbird-provision: no data partition - creating in free space on $DISK"
sgdisk -e -n 0:0:0 -c 0:data -t 0:8300 "$DISK"
sync
echo "superbird-provision: data partition created; rebooting to re-read the GPT"
systemctl reboot
