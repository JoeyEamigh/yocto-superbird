# Shared image base for bridgething prod + dev variants. Lives at
# IMAGE_FSTYPES = squashfs-zst with stock AML MPT partition geometry,
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
# bridgething-weston-init-desktop + bridgething-dev-persist.
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

# Stock AML MPT geometry — works for both prod and dev now that
# squashfs makes the 516 MB slot enough for a kitchen-sink dev
# install (272 MB compressed). 256 MB settings + ~2 GB data is the
# stock layout u-boot already understands.
SUPERBIRD_PART_TABLE = "system_a:0x10600000:0x2040b000,system_b:0x3120b000:0x2040b000,settings:0x52e16000:0x10000000,data:0x63616000:0x859ea000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"
SUPERBIRD_FLASH_VIA_AML_PARTITIONS = "yes"

# Squashfs as the rootfs. zstd compression at level 22 (max) trades
# build time for runtime size — paid once, saves bytes forever.
# -b 1M = 1 MiB squashfs blocks; good locality for chromium-bin's
# mmap'd random-access reads. -no-xattrs skips the xattr table
# (we don't ship SELinux/POSIX-ACL labels). -all-root squashes
# permissions to root:root since the rootfs is single-user anyway.
SUPERBIRD_ROOTFS_TYPE = "squashfs-zst"
EXTRA_IMAGECMD:squashfs-zst = "-b 1M -no-xattrs -all-root -Xcompression-level 22"

# Drop the distro-default ext4 + tar.bz2. Both images get zchunk
# derivatives so bridgething-update-{dev,prod}.bb can each build a
# delta .swu against their respective image. meta-swupdate's
# image_types_zchunk class adds CONVERSIONTYPES `zck` and
# `zckheader`; Yocto walks the dotted chain (squashfs-zst ->
# squashfs-zst.zck -> squashfs-zst.zck.zckheader) and keeps each
# intermediate.
IMAGE_FSTYPES = "squashfs-zst squashfs-zst.zck squashfs-zst.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"
