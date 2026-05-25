SUMMARY = "Cap CPU max frequency based on USB charger detection"
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
