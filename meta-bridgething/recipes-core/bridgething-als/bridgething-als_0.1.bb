SUMMARY = "TMD2772 ambient light to pwm-backlight bridge"
DESCRIPTION = "Small C daemon that polls the TMD2772 clear-photodiode count and adjusts the backlight on a log curve."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-als.c \
    file://bridgething-als.service \
    file://bridgething-als.conf \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-als.service"
# disabled by default; bridgething.service owns the backlight in-daemon
SYSTEMD_AUTO_ENABLE:${PN} = "disable"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o bridgething-als ${S}/bridgething-als.c -lm
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 bridgething-als ${D}${bindir}/bridgething-als

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-als.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/bridgething-als.conf ${D}${sysconfdir}/bridgething-als.conf

    # seed systemd-backlight so first-boot restores above the LP8556 cutoff
    install -d ${D}${localstatedir}/lib/systemd/backlight
    echo 96 > ${D}${localstatedir}/lib/systemd/backlight/platform-backlight:backlight:backlight
}

FILES:${PN} = " \
    ${bindir}/bridgething-als \
    ${systemd_system_unitdir}/bridgething-als.service \
    ${sysconfdir}/bridgething-als.conf \
    ${localstatedir}/lib/systemd/backlight \
"

CONFFILES:${PN} = "${sysconfdir}/bridgething-als.conf"
