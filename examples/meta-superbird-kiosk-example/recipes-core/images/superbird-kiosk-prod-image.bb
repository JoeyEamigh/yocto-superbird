SUMMARY = "Superbird kiosk example prod image"
DESCRIPTION = "ext4 ro rootfs, chromium kiosk against the baked default webapp."
LICENSE = "MIT"

require superbird-kiosk-image-base.inc

IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "4096"

IMAGE_INSTALL:append = " \
    superbird-weston-init-kiosk \
"
