SUMMARY = "Writable timezone state for the read-only rootfs"
DESCRIPTION = "Moves /etc/localtime and /etc/timezone to /var/lib/timezone via symlinks and points systemd-timedated at the writable path."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-timezone.conf \
    file://timezone-state.conf \
"

S = "${UNPACKDIR}"

# tzdata owns the zoneinfo database the symlinks resolve through
RDEPENDS:${PN} = "tzdata"

# replaces /etc/localtime + /etc/timezone; tzdata bbappend strips them from that package
do_install() {
    install -d ${D}${sysconfdir}
    ln -s ../var/lib/timezone/localtime ${D}${sysconfdir}/localtime
    ln -s ../var/lib/timezone/timezone  ${D}${sysconfdir}/timezone

    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${S}/bridgething-timezone.conf \
        ${D}${libdir}/tmpfiles.d/bridgething-timezone.conf

    install -d ${D}${systemd_system_unitdir}/systemd-timedated.service.d
    install -m 0644 ${S}/timezone-state.conf \
        ${D}${systemd_system_unitdir}/systemd-timedated.service.d/timezone-state.conf
}

FILES:${PN} = " \
    ${sysconfdir}/localtime \
    ${sysconfdir}/timezone \
    ${libdir}/tmpfiles.d/bridgething-timezone.conf \
    ${systemd_system_unitdir}/systemd-timedated.service.d/timezone-state.conf \
"
