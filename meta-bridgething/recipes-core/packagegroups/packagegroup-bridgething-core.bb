SUMMARY = "Bridgething core runtime - shared by dev + prod images"
DESCRIPTION = "Everything required for the device to boot to a usable \
bridgething stack: BSP base files, USB-gadget for SSH, weston + Panfrost \
GPU stack, bluetooth, audio, A/B OTA tooling, monotonic-forward clock. \
The kernel ships the hardware drivers built-in (vmlinuz), so this \
packagegroup carries no kernel-module-* RDEPENDS. Image-recipe-only \
concerns (IMAGE_FEATURES, IMAGE_INSTALL extras, partition geometry) \
live in the per-image .bb files."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    \
    bridgething-daemon \
    bridgething-init \
    bridgething-clock \
    bridgething-usb-gadget \
    bridgething-als \
    bridgething-cursor-suppress \
    bridgething-fbpaint \
    bridgething-stock-webapp \
    superbird-base-files \
    superbird-firmware \
    superbird-bluetooth \
    \
    mesa \
    weston \
    blank-cursor \
    \
    chromium-ozone-wayland \
    bridgething-chromium-kiosk \
    \
    swupdate \
    swupdate-config \
    libubootenv-bin \
    bridgething-ab \
    \
    bluez5 \
    alsa-utils \
    zram \
    \
    e2fsprogs \
    e2fsprogs-mke2fs \
    e2fsprogs-e2fsck \
    e2fsprogs-tune2fs \
"
