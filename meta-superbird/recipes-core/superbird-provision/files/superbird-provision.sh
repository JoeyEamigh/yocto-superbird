#!/bin/sh
# First-boot eMMC provisioning for the Spotify Car Thing.
#
# The flashed wic carries only the milestone-1 layout (env + boot_a +
# root_a, ~87 MiB); the rest of the 3.6 GiB eMMC is unallocated. This
# claims that free space as one ext4 `data` partition, which fstab
# mounts at /var (x-systemd.makefs formats it on the next boot).
#
# Runs once. The wic's GPT was sized only for the ~87 MiB image, so
# `sgdisk -e` first relocates the backup GPT to the true end of the
# disk and extends the usable area; then a new partition fills the
# freed space. A reboot follows so the kernel re-reads the fresh GPT.
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
