SUMMARY = "Efuse-derived machine-id and foreign journal cleanup"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-machine-id.sh \
    file://superbird-preinit.sh \
    file://superbird-journal-gc.sh \
    file://superbird-journal-gc.service \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-journal-gc.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${S}/superbird-machine-id.sh ${D}${libexecdir}/superbird-machine-id
    install -m 0755 ${S}/superbird-journal-gc.sh ${D}${libexecdir}/superbird-journal-gc

    install -d ${D}${base_sbindir}
    install -m 0755 ${S}/superbird-preinit.sh ${D}${base_sbindir}/superbird-preinit

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-journal-gc.service ${D}${systemd_system_unitdir}/superbird-journal-gc.service
}

FILES:${PN} = " \
    ${libexecdir}/superbird-machine-id \
    ${libexecdir}/superbird-journal-gc \
    ${base_sbindir}/superbird-preinit \
    ${systemd_system_unitdir}/superbird-journal-gc.service \
"
