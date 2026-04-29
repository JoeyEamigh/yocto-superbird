SUMMARY = "Bridgething production image - kiosk variant"
DESCRIPTION = "Production image: stock AML MPT geometry (516 MB system_a / \
system_b slots, byte-identical to Spotify's stock partitioning). \
Squashfs-zst rootfs with the chromium kiosk launcher auto-starting at \
boot, weston running with kiosk-shell.so + no panel + cursor hidden, \
Panfrost driving the Mali-G31. \
\
Differences from the dev image are install-set-only: kiosk-shell \
weston config, no dev tools, no persistent overlay. Same partition \
geometry, same OTA path, same squashfs format - just the install \
set diverges."
LICENSE = "MIT"

require bridgething-image-base.inc

# Kiosk weston shell + no extra image features beyond the base.
IMAGE_INSTALL:append = " \
    bridgething-weston-init-kiosk \
"

# Emit zchunk artifacts alongside the squashfs rootfs for the
# delta-OTA payload. meta-swupdate's image_types_zchunk class adds
# CONVERSIONTYPES `zck` and `zckheader`; Yocto walks the dotted chain
# (squashfs-zst -> squashfs-zst.zck -> squashfs-zst.zck.zckheader) and
# keeps each intermediate.
IMAGE_FSTYPES += "squashfs-zst.zck squashfs-zst.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"
