SUMMARY = "Templated /opt/<vendor> bind"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://opt-overlay@.service \
    file://opt-overlay-bind \
"
S = "${UNPACKDIR}"

inherit systemd allarch

SYSTEMD_SERVICE:${PN} = "opt-overlay@.service"
SYSTEMD_AUTO_ENABLE = "disable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/opt-overlay@.service ${D}${systemd_system_unitdir}/opt-overlay@.service

    install -d ${D}${libexecdir}
    install -m 0755 ${S}/opt-overlay-bind ${D}${libexecdir}/opt-overlay-bind
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/opt-overlay@.service \
    ${libexecdir}/opt-overlay-bind \
"
