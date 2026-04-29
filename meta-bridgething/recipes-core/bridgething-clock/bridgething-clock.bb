SUMMARY = "Monotonic-forward clock guard for the Superbird"
DESCRIPTION = "The Superbird has no battery-backed RTC, so every cold boot \
starts at systemd's TIME_EPOCH (the build date) - months stale by ship time, \
which breaks SSL cert validity for any HTTPS client on the device (browser \
benchmarks, OTA fetches, anything else). bridgething-clock writes the \
current time to /var/lib/clock-mtime periodically (and on shutdown) and \
forces system time forward to that file's mtime on early boot. systemd's \
TIME_EPOCH is the floor for fresh flashes; this service is the floor for \
every boot after that."
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

# coreutils-style date and stat are in busybox; no extra runtime dep.
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
}

FILES:${PN} = " \
    ${bindir}/bridgething-clock \
    ${systemd_system_unitdir}/bridgething-clock.service \
    ${systemd_system_unitdir}/bridgething-clock-tick.service \
    ${systemd_system_unitdir}/bridgething-clock-tick.timer \
"
