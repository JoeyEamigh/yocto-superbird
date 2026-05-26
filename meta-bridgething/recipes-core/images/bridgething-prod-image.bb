SUMMARY = "Bridgething production image"
DESCRIPTION = "ext4 ro rootfs with the chromium kiosk launcher (cast_shell), weston kiosk-shell, Panfrost driving the Mali-G31."
LICENSE = "MIT"

require bridgething-image-base.inc

IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "4096"

IMAGE_INSTALL:append = " \
    superbird-weston-init-kiosk \
"

BAD_RECOMMENDATIONS += "adwaita-icon-theme-symbolic"

BRIDGETHING_IMAGE_VARIANT = "prod"
