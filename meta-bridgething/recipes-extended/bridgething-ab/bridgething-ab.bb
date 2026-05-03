SUMMARY = "Bridgething A/B slot management CLI (testing helper)"
DESCRIPTION = "Shell wrapper around fw_setenv + swupdate-client \
that lets us drive A/B slot operations end-to-end from the device. \
The bridgething daemon will eventually take this over and expose \
the same surface over the gateway WebSocket; this script is the \
canonical CLI surface in the meantime."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-ab.sh \
    file://bridgething-boot-confirm.service \
"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "swupdate swupdate-client libubootenv-bin"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-boot-confirm.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/bridgething-ab.sh ${D}${bindir}/bridgething-ab

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-boot-confirm.service \
        ${D}${systemd_system_unitdir}/bridgething-boot-confirm.service
}

FILES:${PN} = " \
    ${bindir}/bridgething-ab \
    ${systemd_system_unitdir}/bridgething-boot-confirm.service \
"
