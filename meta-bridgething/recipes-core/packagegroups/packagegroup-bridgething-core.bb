SUMMARY = "Bridgething core runtime"
DESCRIPTION = "Bridgething daemon + hub + stock + weston/chromium stack, on top of packagegroup-superbird-runtime."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    packagegroup-superbird-runtime \
    \
    bridgething-daemon \
    bridgething-stock-webapp \
    bridgething-hub-webapp \
    bridgething-examples \
    bridgething-ab \
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
