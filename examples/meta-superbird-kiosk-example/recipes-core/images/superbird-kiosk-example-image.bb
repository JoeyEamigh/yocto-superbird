SUMMARY = "Superbird kiosk example image"
DESCRIPTION = "BSP + weston-kiosk + chromium cast_shell pointed at a baked default webapp. Copy this layer to start your own kiosk."

require recipes-core/images/superbird-bsp-image.bb

# CHROMIUM_KIOSK_URL is baked into chromium-kiosk at do_install time; override at conf parse time, not here.

IMAGE_INSTALL:append = " \
    superbird-weston-init-kiosk \
    chromium-kiosk \
    superbird-kiosk-default-webapp \
"

IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "4096"
