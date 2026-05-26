SUMMARY = "TMD2772 ambient light to pwm-backlight bridge"
DESCRIPTION = "Small C daemon that polls the TMD2772 clear-photodiode count and adjusts the backlight on a log curve."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-als.c \
    file://superbird-als.service \
    file://superbird-als.conf \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-als.service"
# disabled by default; vendor daemons (e.g., bridgething) that own backlight in-process declare Conflicts=
SYSTEMD_AUTO_ENABLE:${PN} = "disable"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o superbird-als ${S}/superbird-als.c -lm
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 superbird-als ${D}${bindir}/superbird-als

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-als.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/superbird-als.conf ${D}${sysconfdir}/superbird-als.conf

    # seed systemd-backlight so first-boot restores above the LP8556 cutoff
    install -d ${D}${localstatedir}/lib/systemd/backlight
    echo 96 > ${D}${localstatedir}/lib/systemd/backlight/platform-backlight:backlight:backlight
}

FILES:${PN} = " \
    ${bindir}/superbird-als \
    ${systemd_system_unitdir}/superbird-als.service \
    ${sysconfdir}/superbird-als.conf \
    ${localstatedir}/lib/systemd/backlight \
"

CONFFILES:${PN} = "${sysconfdir}/superbird-als.conf"
