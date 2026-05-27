SUMMARY = "Superbird kiosk example dev extras"
DESCRIPTION = "Dev tools + weston desktop shell + VNC. Replaces the kiosk weston shell with the desktop variant. Never lands on the prod image."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    superbird-weston-init-desktop \
    \
    weston-examples \
    weston-vnc-backend \
    libdrm-tests \
    kmscube \
    superbird-gltest \
    wayland-utils \
    \
    bash \
    coreutils \
    util-linux \
    iproute2 \
    \
    vim-tiny \
    htop \
    strace \
    tcpdump \
    \
    i2c-tools \
    evtest \
    \
    fastfetch \
"
