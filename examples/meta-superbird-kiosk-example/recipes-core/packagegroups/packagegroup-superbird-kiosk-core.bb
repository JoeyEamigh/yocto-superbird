SUMMARY = "Superbird kiosk example core runtime"
DESCRIPTION = "BSP runtime + graphics + chromium kiosk + the example daemon + the placeholder webapp. Weston desktop-vs-kiosk picked at the image level."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    packagegroup-superbird-runtime \
    \
    superbird-kiosk-daemon \
    superbird-kiosk-default-webapp \
    \
    mesa \
    weston \
    blank-cursor \
    cursor-suppress \
    superbird-fbpaint \
    \
    chromium-ozone-wayland \
    chromium-kiosk \
"
