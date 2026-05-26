SUMMARY = "Monotonic-forward clock guard"
DESCRIPTION = "No battery-backed RTC; this writes the current time to /var/lib/clock-mtime and forces system time forward to that mtime on early boot."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-clock.sh \
    file://superbird-clock.service \
    file://superbird-clock-tick.service \
    file://superbird-clock-tick.timer \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = " \
    superbird-clock.service \
    superbird-clock-tick.service \
    superbird-clock-tick.timer \
"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = ""

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/superbird-clock.sh ${D}${bindir}/superbird-clock

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-clock.service \
        ${D}${systemd_system_unitdir}/superbird-clock.service
    install -m 0644 ${S}/superbird-clock-tick.service \
        ${D}${systemd_system_unitdir}/superbird-clock-tick.service
    install -m 0644 ${S}/superbird-clock-tick.timer \
        ${D}${systemd_system_unitdir}/superbird-clock-tick.timer

    # mask systemd-timesyncd; we own clock semantics and timesyncd has no ntp path here
    install -d ${D}${sysconfdir}/systemd/system
    ln -sf /dev/null ${D}${sysconfdir}/systemd/system/systemd-timesyncd.service
}

FILES:${PN} = " \
    ${bindir}/superbird-clock \
    ${systemd_system_unitdir}/superbird-clock.service \
    ${systemd_system_unitdir}/superbird-clock-tick.service \
    ${systemd_system_unitdir}/superbird-clock-tick.timer \
    ${sysconfdir}/systemd/system/systemd-timesyncd.service \
"
