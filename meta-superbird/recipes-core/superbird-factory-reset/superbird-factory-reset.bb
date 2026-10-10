SUMMARY = "Boot-time data partition wipe for factory reset"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-factory-reset.sh \
    file://superbird-factory-reset.service \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-factory-reset.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "libubootenv-bin util-linux-wipefs"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${S}/superbird-factory-reset.sh ${D}${libexecdir}/superbird-factory-reset

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-factory-reset.service ${D}${systemd_system_unitdir}/superbird-factory-reset.service
}

FILES:${PN} = " \
    ${libexecdir}/superbird-factory-reset \
    ${systemd_system_unitdir}/superbird-factory-reset.service \
"
