SUMMARY = "Bridgething core runtime"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    \
    bridgething-daemon \
    opt-overlay \
    bridgething-init \
    superbird-clock \
    superbird-timezone \
    superbird-usb-gadget \
    superbird-mdns \
    superbird-als \
    cursor-suppress \
    superbird-fbpaint \
    bridgething-stock-webapp \
    bridgething-hub-webapp \
    superbird-base-files \
    superbird-firmware \
    superbird-bluetooth \
    \
    mesa \
    weston \
    blank-cursor \
    \
    chromium-ozone-wayland \
    chromium-kiosk \
    \
    swupdate \
    swupdate-config \
    libubootenv-bin \
    bridgething-ab \
    \
    bluez5 \
    alsa-utils \
    zram \
    tzdata \
    \
    e2fsprogs \
    e2fsprogs-mke2fs \
    e2fsprogs-e2fsck \
    e2fsprogs-tune2fs \
"
