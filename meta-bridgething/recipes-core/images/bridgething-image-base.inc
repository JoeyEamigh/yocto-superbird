# Shared image base for bridgething prod + dev variants. Lives at
# IMAGE_FSTYPES = squashfs-lz4 with stock AML MPT partition geometry,
# so dev + prod produce byte-identical layouts. The only differences
# the per-variant recipes layer on top are install-set extras (cog,
# chromedriver, dev tools), the weston shell choice (-kiosk vs
# -desktop), and the persistent /opt overlay (dev only). Everything
# size-pressure-driven (the 1024 MB dev slot, ext4 -m 0 / -i 16384,
# bridgething_prod_slim post-process) is gone — squashfs compresses
# everything we care about cutting down to near-zero anyway.

inherit core-image
inherit superbird-flashthing

# Locale + SwiftShader trim still applies on both images. Saves ~65
# MB pre-compression / ~15-20 MB post-squashfs and has zero runtime
# cost (English-only kiosk, Panfrost provides GL so SwiftShader's
# software-Vulkan fallback is dead weight).
require bridgething-chromium-slim.inc

# Shared image features. Auth posture is intentionally open: the
# project exists to give the Car Thing back to its owners after
# Spotify abandoned it; trust boundary, where one exists, lives at
# the Bluetooth gateway pair, not the device. Variants override
# `allow-empty-password / allow-root-login / empty-root-password`
# below if they want a development-friendlier debug auth setup.
IMAGE_FEATURES += "ssh-server-openssh post-install-logging"

# Shared install set. Per-variant recipes append on top: prod adds
# bridgething-weston-init-kiosk; dev adds the dev packagegroup +
# bridgething-weston-init-desktop. The persistent /opt/bridgething
# overlay (settings-partition bind) ships in both via the core
# packagegroup.
IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-bridgething-core \
"

# Block transitive RRECOMMENDS that drag in dead weight. Most drivers
# we use are built into vmlinuz (kernel cfg fragment); the few we keep
# out are pulled in by name below. The kernel-modules meta-package
# would otherwise drag in every .ko the upstream arm64 defconfig leaves
# as =m (~hundreds of MiB of unrelated drivers). udev-hwdb is desktop
# bloat; wpa-supplicant + wireless-regdb assume wifi we don't have.
BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static"

# Per-driver kernel module packages. Each pulls a specific .ko under
# /lib/modules; udev autoloads on DT match. Items here are =m in the
# kernel cfg fragment because linking them into vmlinuz pushes boot.img
# past the 16 MiB boot_a partition cap.
IMAGE_INSTALL += " \
    kernel-module-meson-vdec \
    kernel-module-ge2d \
"

# Stock AML MPT geometry — works for both prod and dev. 516 MB
# system slot each, 256 MB settings, ~2 GB data. u-boot already
# understands this layout from the stock partition table.
SUPERBIRD_PART_TABLE = "system_a:0x10600000:0x2040b000,system_b:0x3120b000:0x2040b000,settings:0x52e16000:0x10000000,data:0x63616000:0x859ea000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"
SUPERBIRD_FLASH_VIA_AML_PARTITIONS = "yes"

# Rootfs type is overridable per-image. Default is squashfs-lz4
# (chosen for fast lz4 decompress on Cortex-A53). The prod image
# overrides to ext4 ro because cast_shell shrinks the rootfs enough
# that prod fits a 516 MiB ext4 partition with headroom, and ext4 ro
# avoids the squashfs decompress threads' sustained PSI memory
# pressure on cold M-key (200-400 ms win measured on this hardware).
# Dev keeps squashfs because its kitchen-sink install doesn't fit.
#
# Per-fstype EXTRA_IMAGECMD:
#   squashfs-lz4 — -b 1M = 1 MiB blocks (good locality for chromium-
#     bin mmap reads). -Xhc = high-compression encoder; same lz4 wire
#     format the kernel already decompresses at ~1.5-2 GB/s on A53.
#     -no-xattrs skips the xattr table (no SELinux / POSIX-ACL
#     labels). -all-root squashes perms to root:root.
#   ext4 — -i 16384 = one inode per 16 KiB (~30K inodes for a 500
#     MiB FS, comfortable). -m 0 = drop the 5% superuser reserve
#     (~25 MiB on a 516 MiB FS, we don't write to the rootfs at
#     runtime). -O ^has_journal disables ext4 journaling (rootfs
#     mounts ro, journal is pure overhead). 64-bit + huge_file off
#     since rootfs is well under 2 TB.
SUPERBIRD_ROOTFS_TYPE ??= "squashfs-lz4"
EXTRA_IMAGECMD:squashfs-lz4 = "-b 1M -no-xattrs -all-root -Xhc"
EXTRA_IMAGECMD:ext4 = "-i 16384 -m 0 -O ^has_journal -O ^huge_file"

# Drop the distro-default ext4 + tar.bz2. Both images get zchunk
# derivatives so bridgething-update-{dev,prod}.bb can each build a
# delta .swu against their respective image. meta-swupdate's
# image_types_zchunk class adds CONVERSIONTYPES `zck` and
# `zckheader`; Yocto walks the dotted chain (<base> ->
# <base>.zck -> <base>.zck.zckheader) and keeps each intermediate.
IMAGE_FSTYPES = "${SUPERBIRD_ROOTFS_TYPE} ${SUPERBIRD_ROOTFS_TYPE}.zck ${SUPERBIRD_ROOTFS_TYPE}.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"

# OTA channel + image variant + image version baked into the running
# image's /etc/superbird so the companion's manifest poll loop knows
# which channel to follow and which artifact path to fetch (auto-push
# is gated on channel match; mismatch surfaces as a UI prompt for a
# full reflash because cross-channel zcks won't line up). These knobs
# live at image-recipe scope rather than at bridgething-init scope
# because the package recipe sees neither the variant nor the channel.
# Per-image bb files override `BRIDGETHING_IMAGE_VARIANT` (and dev
# overrides `BRIDGETHING_CHANNEL`); `BRIDGETHING_IMAGE_VERSION` falls
# back to DISTRO_VERSION when not pinned by a release cut.
BRIDGETHING_CHANNEL ?= "stable"
BRIDGETHING_IMAGE_VARIANT ?= "prod"
BRIDGETHING_IMAGE_VERSION ?= "${DISTRO_VERSION}"

bridgething_meta_postprocess() {
    sed -i \
        -e "s|@BRIDGETHING_CHANNEL@|${BRIDGETHING_CHANNEL}|g" \
        -e "s|@BRIDGETHING_IMAGE_VARIANT@|${BRIDGETHING_IMAGE_VARIANT}|g" \
        -e "s|@BRIDGETHING_IMAGE_VERSION@|${BRIDGETHING_IMAGE_VERSION}|g" \
        ${IMAGE_ROOTFS}${datadir}/superbird/meta.json.in
}
IMAGE_PREPROCESS_COMMAND:append = " bridgething_meta_postprocess; "
