SUMMARY = "Superbird mic debug runtime"
DESCRIPTION = "BSP runtime + graphics + chromium kiosk + the recorder, plus USB host class drivers"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

USB_HOST_MODULES = " \
    kernel-module-scsi-mod \
    kernel-module-sd-mod \
    kernel-module-usb-storage \
    kernel-module-uas \
    kernel-module-exfat \
"

RDEPENDS:${PN} = " \
    packagegroup-superbird-runtime \
    \
    mic-debug-daemon \
    ${USB_HOST_MODULES} \
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
