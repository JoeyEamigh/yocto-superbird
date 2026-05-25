SUMMARY = "Superbird BSP-only image"
DESCRIPTION = "Mainline kernel + busybox + openssh + USB-CDC-NCM gadget. No avahi, no bridgething daemon, no chromium. Useful as a BSP / kernel iteration base."
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
    superbird-provision \
    superbird-cpufreq-cap \
    superbird-slot-ok \
"

BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static"

# Emit a GPT disk image via wic (layout in superbird-mainline.wks) instead of
# the stock flashthing zip. squashfs is also emitted standalone so root_a can
# be flashed on its own during bring-up.
IMAGE_FSTYPES = "wic squashfs"
WKS_FILE = "superbird-mainline.wks"

# Artifacts wic pulls from the deploy dir: kernel+DTB, the env FAT image, extlinux.conf.
do_image_wic[depends] += " \
    virtual/kernel:do_deploy \
    superbird-uenv:do_deploy \
    superbird-extlinux:do_deploy \
"

# u-boot lives in boot0/boot1, not the wic GPT - so it's a sibling deploy
# artifact, not a wic input. EXTRA_IMAGEDEPENDS builds + deploys superbird-boot.bin.
EXTRA_IMAGEDEPENDS += "superbird-uboot"
