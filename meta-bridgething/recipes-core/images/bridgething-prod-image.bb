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
