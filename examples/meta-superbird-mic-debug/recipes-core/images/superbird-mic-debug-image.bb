SUMMARY = "Superbird microphone recording image"
DESCRIPTION = "Records the raw array to a USB drive behind a powered hub and shows the live state on the panel"
LICENSE = "MIT"

inherit core-image
inherit superbird-headroom-check
inherit superbird-image
inherit mainline-flashthing

IMAGE_FEATURES += "ssh-server-openssh tools-debug post-install-logging"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-superbird-mic-debug \
    superbird-weston-init-kiosk \
"

BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static weston-init"
BAD_RECOMMENDATIONS += "adwaita-icon-theme-symbolic"

SUPERBIRD_ROOTFS_TYPE = "ext4"
EXTRA_IMAGECMD:ext4 = "-i 16384 -m 0 -O ^has_journal -O ^huge_file"
IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "4096"

IMAGE_FSTYPES = "wic ${SUPERBIRD_ROOTFS_TYPE}"

WKS_FILE = "superbird-mainline.wks.in"

do_image_wic[depends] += " \
    virtual/kernel:do_deploy \
    superbird-uenv:do_deploy \
    superbird-extlinux:do_deploy \
    superbird-logo:do_deploy \
"

EXTRA_IMAGEDEPENDS += "superbird-uboot"
