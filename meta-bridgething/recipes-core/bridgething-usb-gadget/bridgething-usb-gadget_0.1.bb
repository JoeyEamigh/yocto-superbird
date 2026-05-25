SUMMARY = "Bridgething USB gadget bring-up"
DESCRIPTION = "Composite CDC-NCM + FunctionFS-ADB gadget on the DWC2 controller. Per-serial /29 subnet, DHCP via systemd-networkd, mDNS via avahi."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://bridgething-usb-gadget.sh \
    file://bridgething-usb-gadget.service \
    file://11-usb-ncm.network \
    file://adbd-bridgething.conf \
    file://usb-debugging-enabled \
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
    install -m 0644 ${S}/11-usb-ncm.network ${D}${sysconfdir}/systemd/network/

    # drop-in retargets android-tools-adbd at the composite gadget
    install -d ${D}${systemd_system_unitdir}/android-tools-adbd.service.d
    install -m 0644 ${S}/adbd-bridgething.conf \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service.d/bridgething.conf

    # ConditionPathExists gate; remove the file on-device to disable adbd at boot
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/usb-debugging-enabled ${D}${sysconfdir}/usb-debugging-enabled
}

FILES:${PN} = "\
    ${libexecdir}/bridgething-usb-gadget \
    ${systemd_system_unitdir}/bridgething-usb-gadget.service \
    ${systemd_system_unitdir}/android-tools-adbd.service.d/bridgething.conf \
    ${sysconfdir}/systemd/network/11-usb-ncm.network \
    ${sysconfdir}/usb-debugging-enabled \
"

RDEPENDS:${PN} = "bash android-tools-adbd"
