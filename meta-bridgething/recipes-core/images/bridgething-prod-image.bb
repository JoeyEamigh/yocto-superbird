SUMMARY = "Bridgething production image - kiosk variant"
DESCRIPTION = "Production image: stock AML MPT geometry (516 MB system_a / \
system_b slots, byte-identical to Spotify's stock partitioning). \
ext4 ro rootfs with the chromium-kiosk launcher (exec'ing cast_shell, \
shipped as /usr/lib/chromium/chromium-bin) auto-starting at boot, \
weston running with kiosk-shell.so + no panel + cursor hidden, \
Panfrost driving the Mali-G31. \
\
Differences from the dev image are: ext4 ro vs squashfs-lz4 (the \
smaller cast_shell binary plus cruft cuts make the prod rootfs fit a \
516 MiB ext4 partition with headroom, and ext4 avoids the squashfs \
decompress threads' sustained PSI memory pressure on cold M-key), \
plus install-set divergence (no cog/wpewebkit, no dev tools, no \
persistent overlay). Same partition geometry, same OTA path - the \
.swu's system.img cpio entry is filesystem-agnostic."
LICENSE = "MIT"

# Pinned BEFORE require so bridgething-image-base.inc's IMAGE_FSTYPES
# expansion picks up the ext4 type. Using = instead of ??= because we
# want prod-image to be authoritative regardless of parse order.
SUPERBIRD_ROOTFS_TYPE = "ext4"

require bridgething-image-base.inc

# Size discipline: the 516 MiB system_a partition (0x2040b000) bounds
# the ext4 image. Yocto's default IMAGE_OVERHEAD_FACTOR=1.3 + the
# class's auto-sizing would land beyond that on the kitchen-sink
# rootfs. Drop the overhead factor to 1.0 and budget extra space via
# IMAGE_ROOTFS_EXTRA_SPACE (KiB) so mkfs.ext4 produces a tight image
# that fits the partition with headroom for ext4 metadata overhead
# (~1.5% on flat block layouts).
IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "32768"

# Kiosk weston shell + cast_shell-equivalent install set.
IMAGE_INSTALL:append = " \
    bridgething-weston-init-kiosk \
"

# Prod cruft cuts: gtk+3 / nss / nspr / icon-theme stack is removed
# via the chromium-ozone-wayland bbappend's RDEPENDS:remove when
# cast-shell PACKAGECONFIG is selected, so those drop automatically.
# adwaita-icon-theme-symbolic is the only one that might still be
# pulled if another recipe RRECOMMENDS it; belt-and-suspenders here.
BAD_RECOMMENDATIONS += "adwaita-icon-theme-symbolic"

# Mark the running image's OTA identity. Channel defaults to stable
# in bridgething-init.bb; pinning the variant explicitly here keeps
# /etc/superbird honest if the default ever drifts.
BRIDGETHING_IMAGE_VARIANT = "prod"
