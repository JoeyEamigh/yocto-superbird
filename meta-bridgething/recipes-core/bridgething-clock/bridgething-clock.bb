SUMMARY = "Monotonic-forward clock guard"
DESCRIPTION = "No battery-backed RTC; this writes the current time to /var/lib/clock-mtime and forces system time forward to that mtime on early boot."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-clock.sh \
    file://bridgething-clock.service \
    file://bridgething-clock-tick.service \
    file://bridgething-clock-tick.timer \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = " \
    bridgething-clock.service \
    bridgething-clock-tick.service \
    bridgething-clock-tick.timer \
"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = ""

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/bridgething-clock.sh ${D}${bindir}/bridgething-clock

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-clock.service \
        ${D}${systemd_system_unitdir}/bridgething-clock.service
    install -m 0644 ${S}/bridgething-clock-tick.service \
        ${D}${systemd_system_unitdir}/bridgething-clock-tick.service
    install -m 0644 ${S}/bridgething-clock-tick.timer \
        ${D}${systemd_system_unitdir}/bridgething-clock-tick.timer

    # mask systemd-timesyncd; we own clock semantics and timesyncd has no ntp path here
    install -d ${D}${sysconfdir}/systemd/system
    ln -sf /dev/null ${D}${sysconfdir}/systemd/system/systemd-timesyncd.service
}

FILES:${PN} = " \
    ${bindir}/bridgething-clock \
    ${systemd_system_unitdir}/bridgething-clock.service \
    ${systemd_system_unitdir}/bridgething-clock-tick.service \
    ${systemd_system_unitdir}/bridgething-clock-tick.timer \
    ${sysconfdir}/systemd/system/systemd-timesyncd.service \
"
