#!/bin/sh
# First-boot eMMC provisioning: claim the space the wic didn't fill as one
# ext4 `data` partition (fstab mounts it at /var; x-systemd.makefs formats it
# next boot). The wic's GPT is sized to the flashed image, so sgdisk -e first
# relocates the backup GPT to the true end of disk and extends the usable
# area before carving data. Reboot follows so the kernel re-reads the GPT.
set -e

DISK=/dev/mmcblk0

if sgdisk -p "$DISK" 2>/dev/null | grep -qw data; then
    # data partition already present - nothing to do.
    exit 0
fi

echo "superbird-provision: no data partition - creating in free space on $DISK"
sgdisk -e -n 0:0:0 -c 0:data -t 0:8300 "$DISK"
sync
echo "superbird-provision: data partition created; rebooting to re-read the GPT"
systemctl reboot
