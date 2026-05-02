SUMMARY = "Bridgething USB gadget bring-up (CDC-ECM + RNDIS) for dev-iteration SSH"
DESCRIPTION = "Configures a multi-config USB-gadget on the Superbird's DWC2 \
peripheral controller at boot. Configuration 1 exposes RNDIS (Microsoft OS \
descriptors steer Windows to its inbox driver); configuration 2 exposes \
CDC-ECM for Linux/macOS and Windows 10 1809+ inbox CDC-ECM. systemd-networkd \
runs an internal DHCP server on whichever interface the host activates so \
the device is plug-and-play across all three OSes - 10.42.1.2 is the device, \
the host gets a lease in 10.42.1.10-17."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://bridgething-usb-gadget.sh \
    file://bridgething-usb-gadget.service \
    file://10-usb-rndis.network \
    file://11-usb-ecm.network \
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
    install -m 0644 ${S}/10-usb-rndis.network ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${S}/11-usb-ecm.network   ${D}${sysconfdir}/systemd/network/
}

FILES:${PN} = "\
    ${libexecdir}/bridgething-usb-gadget \
    ${systemd_system_unitdir}/bridgething-usb-gadget.service \
    ${sysconfdir}/systemd/network/10-usb-rndis.network \
    ${sysconfdir}/systemd/network/11-usb-ecm.network \
"

RDEPENDS:${PN} = "bash"
