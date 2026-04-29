SUMMARY = "Bridgething USB gadget bring-up (CDC-ECM) for dev-iteration SSH"
DESCRIPTION = "Configures a CDC-ECM + RNDIS composite gadget on the Superbird's \
DWC2 peripheral controller at boot so the device appears as a USB network \
interface to a connected host. Intended for dev iteration (rsync, SSH)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://bridgething-usb-gadget.sh \
    file://bridgething-usb-gadget.service \
    file://10-usb0.network \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-usb-gadget.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${S}/bridgething-usb-gadget.sh ${D}${libexecdir}/bridgething-usb-gadget

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-usb-gadget.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${S}/10-usb0.network ${D}${sysconfdir}/systemd/network/
}

FILES:${PN} = "\
    ${libexecdir}/bridgething-usb-gadget \
    ${systemd_system_unitdir}/bridgething-usb-gadget.service \
    ${sysconfdir}/systemd/network/10-usb0.network \
"

RDEPENDS:${PN} = "bash"
