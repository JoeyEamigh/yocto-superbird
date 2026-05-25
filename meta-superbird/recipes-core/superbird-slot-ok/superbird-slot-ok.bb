SUMMARY = "Reset the active A/B slot's try counter after stable uptime"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-slot-ok.sh \
    file://superbird-slot-ok.service \
    file://superbird-slot-ok.timer \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-slot-ok.timer"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "libubootenv-bin"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/superbird-slot-ok.sh ${D}${libexecdir}/superbird-slot-ok

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/superbird-slot-ok.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/superbird-slot-ok.timer   ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${libexecdir}/superbird-slot-ok \
    ${systemd_system_unitdir}/superbird-slot-ok.service \
    ${systemd_system_unitdir}/superbird-slot-ok.timer \
"
