SUMMARY = "TMD2772 ambient light → pwm-backlight bridge"
DESCRIPTION = "Tiny C daemon that polls the TMD2772 clear-photodiode \
raw count from IIO and adjusts /sys/class/backlight/backlight/brightness \
on a log curve. Replaces what GNOME's iio-sensor-proxy + a brightness \
daemon would do, but in 100 lines of C with no DBus."
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
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

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

    # Pre-populated systemd-backlight save so the first boot (before
    # bridgething-als has its first sample) restores a sane brightness
    # instead of 8 (below the LP8556 cutoff). systemd-backlight will
    # overwrite this on every clean shutdown with whatever the brightness
    # was at the time, so this file only matters for the very first boot
    # (or any boot where the file was deleted).
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
