SUMMARY = "Superbird BSP-only image - kernel + busybox + SSH"
DESCRIPTION = "Smallest flashable image for the Spotify Car Thing. \
Mainline kernel with the BSP patches (panel, BT, touchscreen, rotary, \
ALS, pinctrl), busybox userspace, openssh, and the USB-CDC-ECM gadget. \
\
mainline-uboot branch: boots via elle'"'"'s mainline u-boot (extlinux), \
GPT user-area layout produced by wic. No stock-Amlogic flashthing zip. \
Useful as a BSP-bringup / kernel-iteration target."
LICENSE = "MIT"

inherit core-image

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
    superbird-base-files \
    superbird-firmware \
    superbird-bluetooth \
    bridgething-usb-gadget \
    bluez5 \
    e2fsprogs \
    e2fsprogs-mke2fs \
    e2fsprogs-e2fsck \
    e2fsprogs-tune2fs \
    libubootenv-bin \
"

BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static"

# mainline-uboot: emit a GPT user-area disk image via wic instead of the
# stock-Amlogic flashthing zip. Layout in superbird-mainline.wks:
#   env (uboot.env) + boot_a (kernel/dtb/extlinux) + root_a (squashfs).
# squashfs is also emitted standalone so the root_a partition content can
# be flashed on its own during bring-up testing.
IMAGE_FSTYPES = "wic squashfs"
WKS_FILE = "superbird-mainline.wks"

# wic consumes deploy-dir artifacts from these recipes: the kernel + DTB
# (bootimg-partition / IMAGE_BOOT_FILES), the env FAT image (env rawcopy
# partition), and extlinux.conf (bootimg-partition / IMAGE_BOOT_FILES).
do_image_wic[depends] += " \
    virtual/kernel:do_deploy \
    superbird-uenv:do_deploy \
    superbird-extlinux:do_deploy \
"
