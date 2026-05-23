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

# Display bring-up. bridgething-weston-init-desktop is joey's known-good
# boot-Weston setup (systemd unit + desktop-shell weston.ini bound to
# DSI-1 480x800, rotated for landscape) - it RDEPENDS weston itself, so
# the BSP image gets a real compositor on the panel. bridgething-cursor-
# suppress is the weston module that weston.ini's [core] modules= loads -
# weston aborts at startup if it's absent, and weston-init doesn't
# RDEPEND it (the bridgething packagegroup normally pulls it). weston-vnc-
# backend satisfies the vnc-backend.so the desktop variant's dev env
# references; weston-examples gives demo clients for confirming render.
IMAGE_INSTALL += " \
    bridgething-weston-init-desktop \
    bridgething-cursor-suppress \
    weston-vnc-backend \
    weston-examples \
"


# Audio test tooling. Card 0 (axg-sound-card / amlogic,g12a-pdm) is plumbed
# end-to-end at the kernel level but the BSP image has no userspace tools to
# exercise it. alsa-utils gives us arecord (capture from the 4-mic PDM array),
# amixer/alsactl (mixer + state management), and the supporting infra.
IMAGE_INSTALL += " \
    alsa-utils \
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
