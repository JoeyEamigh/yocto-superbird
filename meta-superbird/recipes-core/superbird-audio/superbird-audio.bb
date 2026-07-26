SUMMARY = "Sound card mixer state"
DESCRIPTION = "Ships the PDM capture routing as an alsactl state file on the read-only rootfs and points alsa-restore at it."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://asound.state \
    file://alsa-restore-state.conf \
"

S = "${UNPACKDIR}"

RDEPENDS:${PN} = "alsa-utils-alsactl"

do_install() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/asound.state ${D}${sysconfdir}/asound.state

    install -d ${D}${systemd_system_unitdir}/alsa-restore.service.d
    install -m 0644 ${S}/alsa-restore-state.conf \
        ${D}${systemd_system_unitdir}/alsa-restore.service.d/alsa-restore-state.conf
}

FILES:${PN} = " \
    ${sysconfdir}/asound.state \
    ${systemd_system_unitdir}/alsa-restore.service.d/alsa-restore-state.conf \
"
