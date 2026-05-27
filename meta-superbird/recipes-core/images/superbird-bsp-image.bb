SUMMARY = "Superbird BSP-only image"
DESCRIPTION = "Mainline kernel + busybox + openssh + USB-CDC-NCM gadget for bring-up. No graphics, no application. Fork examples/meta-superbird-kiosk-example/ to layer on a kiosk."
LICENSE = "MIT"

inherit core-image
inherit superbird-headroom-check
inherit superbird-image
inherit mainline-flashthing

IMAGE_FEATURES += " \
    ssh-server-openssh \
    allow-empty-password \
    allow-root-login \
    empty-root-password \
    post-install-logging \
    serial-autologin-root \
"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-superbird-runtime \
"

BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static weston-init"

# standalone squashfs so root_a can be flashed alone during bring-up.
IMAGE_FSTYPES = "wic squashfs"
WKS_FILE = "superbird-mainline.wks.in"

do_image_wic[depends] += " \
    virtual/kernel:do_deploy \
    superbird-uenv:do_deploy \
    superbird-extlinux:do_deploy \
    superbird-logo:do_deploy \
"

# u-boot ships separately for boot0/boot1, not packed into the wic gpt.
EXTRA_IMAGEDEPENDS += "superbird-uboot"
