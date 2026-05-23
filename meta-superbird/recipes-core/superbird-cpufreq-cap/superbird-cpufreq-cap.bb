SUMMARY = "Cap CPU max frequency based on USB charger detection"
DESCRIPTION = "u-boot reads the MAX14656 charger-detect IC and appends a \
superbird.max_cpufreq_khz= parameter to the kernel command line based on \
the detected port class (SDP-500 / SDP-HC / CDP / DCP). This systemd \
oneshot fires early under sysinit.target, parses /proc/cmdline, and \
writes scaling_max_freq accordingly. If the param is absent the cap \
defaults to 1.5 GHz (safe baseline for SDP-500)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-cpufreq-cap.sh \
    file://superbird-cpufreq-cap.service \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-cpufreq-cap.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/superbird-cpufreq-cap.sh ${D}${libexecdir}/superbird-cpufreq-cap

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/superbird-cpufreq-cap.service \
        ${D}${systemd_system_unitdir}/superbird-cpufreq-cap.service
}

FILES:${PN} = " \
    ${libexecdir}/superbird-cpufreq-cap \
    ${systemd_system_unitdir}/superbird-cpufreq-cap.service \
"
