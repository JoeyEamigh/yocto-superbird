SUMMARY = "Superbird BSP-only image - kernel + busybox + SSH"
DESCRIPTION = "Smallest flashable image for the Spotify Car Thing. \
Mainline kernel with the BSP patches (panel, BT, touchscreen, rotary, \
ALS, pinctrl), busybox userspace, openssh, and the USB-CDC-ECM gadget. \
\
mainline-uboot branch: boots via elle's mainline u-boot (extlinux), \
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
    superbird-provision \
    superbird-cpufreq-cap \
    superbird-slot-ok \
"

# Display bring-up. weston-init-desktop is joey's known-good boot-Weston
# (DSI-1 480x800, rotated landscape) and RDEPENDS weston. cursor-suppress
# is load-bearing: weston.ini's [core] modules= loads it and weston aborts
# at startup if it's absent, but weston-init doesn't RDEPEND it. vnc-backend
# + examples are for the dev env / confirming render.
IMAGE_INSTALL += " \
    bridgething-weston-init-desktop \
    bridgething-cursor-suppress \
    weston-vnc-backend \
    weston-examples \
"


# Audio test tooling - arecord/amixer to exercise the PDM mic array (card 0,
# axg-sound-card), which is kernel-plumbed but has no userspace tools otherwise.
IMAGE_INSTALL += " \
    alsa-utils \
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
