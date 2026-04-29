SUMMARY = "Bring up the Superbird's BCM20703A2 Bluetooth controller"
DESCRIPTION = "Pulses GPIOX_17 (chip enable / REG_ON) via libgpiod, \
then attaches the bluez btattach line discipline to /dev/ttyAML1 with \
the Broadcom protocol. Mirrors stock's bluetooth-adapter \
service - same chip, same UART, known-working approach."
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
# Userspace btattach is the PRIMARY BT bring-up path on this image.
# The mainline hci_bcm serdev auto-bind path was tried with the right
# patches (subver entry, 250 ms post-Reset settle, BROKEN_READ_TRANSMIT
# _POWER quirk, no_early_set_baudrate of_match entry, 500 ms post-
# power-on wait) and still hits a Read_Local_Version timeout on this
# specific BCM20703A2. Stock 4.9 uses btattach + bcm protocol;
# we mirror that. DTS has no `bluetooth { }` child of &uart_A so
# /dev/ttyAML1 stays as a plain tty that btattach can claim.
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
