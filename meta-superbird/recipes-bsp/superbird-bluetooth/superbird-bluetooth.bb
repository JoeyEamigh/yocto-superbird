SUMMARY = "Superbird BCM20703A2 bluetooth bring-up"
DESCRIPTION = "Pulses GPIOX_17 to enable the chip and runs btattach against /dev/ttyAML1 with the broadcom protocol."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-bluetooth.service \
    file://superbird-bluetooth.sh \
"

S = "${UNPACKDIR}"

RDEPENDS:${PN} = "bluez5 libgpiod-tools bash"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-bluetooth.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/superbird-bluetooth.sh \
        ${D}${bindir}/superbird-bluetooth

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-bluetooth.service \
        ${D}${systemd_system_unitdir}/superbird-bluetooth.service
}

FILES:${PN} = " \
    ${bindir}/superbird-bluetooth \
    ${systemd_system_unitdir}/superbird-bluetooth.service \
"
