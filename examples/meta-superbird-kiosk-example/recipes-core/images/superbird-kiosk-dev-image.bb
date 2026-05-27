SUMMARY = "Superbird kiosk example dev image"
DESCRIPTION = "squashfs-lz4 rootfs with weston desktop + VNC + dev tools. Iterate from here, then graduate to the prod image."
LICENSE = "MIT"

SUPERBIRD_ROOTFS_TYPE = "squashfs-lz4"

require superbird-kiosk-image-base.inc

IMAGE_FEATURES += "tools-debug post-install-logging"

IMAGE_INSTALL:append = " \
    packagegroup-superbird-kiosk-dev \
"
