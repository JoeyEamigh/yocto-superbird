SUMMARY = "Writable timezone state for the read-only rootfs"
DESCRIPTION = "Stores the system timezone in /var/lib/timezone for systemd-timedated."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-timezone.conf \
    file://timezone-state.conf \
"

S = "${UNPACKDIR}"

RDEPENDS:${PN} = "tzdata"

do_install() {
    install -d ${D}${sysconfdir}
    ln -s ../var/lib/timezone/localtime ${D}${sysconfdir}/localtime

    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${S}/superbird-timezone.conf \
        ${D}${libdir}/tmpfiles.d/superbird-timezone.conf

    install -d ${D}${systemd_system_unitdir}/systemd-timedated.service.d
    install -m 0644 ${S}/timezone-state.conf \
        ${D}${systemd_system_unitdir}/systemd-timedated.service.d/timezone-state.conf
}

FILES:${PN} = " \
    ${sysconfdir}/localtime \
    ${libdir}/tmpfiles.d/superbird-timezone.conf \
    ${systemd_system_unitdir}/systemd-timedated.service.d/timezone-state.conf \
"
