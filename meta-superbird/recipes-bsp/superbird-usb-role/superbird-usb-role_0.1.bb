SUMMARY = "USB port role helper"
DESCRIPTION = "Runtime device/host role switching for the OTG port, with timed revert and optional boot persistence."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-usb-role \
    file://superbird-usb-role.service \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-usb-role.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/superbird-usb-role ${D}${bindir}/superbird-usb-role

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-usb-role.service \
        ${D}${systemd_system_unitdir}/superbird-usb-role.service
}

FILES:${PN} = " \
    ${bindir}/superbird-usb-role \
    ${systemd_system_unitdir}/superbird-usb-role.service \
"
