SUMMARY = "Bridgething USB gadget bring-up (RNDIS + CDC-ECM + ADB) for dev-iteration SSH/ADB"
DESCRIPTION = "Configures a single-config composite USB-gadget on the \
Superbird's DWC2 peripheral controller at boot. The configuration carries \
three functions: RNDIS (Microsoft OS descriptors steer Windows to its inbox \
driver), CDC-ECM (Linux/macOS/Win10+ inbox), and FunctionFS-backed ADB \
(adbd writes the descriptors before the UDC bind). The two network \
interfaces are bridge ports under usb-br0 so traffic flows through whichever \
one the host activated; systemd-networkd runs an internal DHCP server on \
the bridge handing out 10.42.1.10-17 to the host while the device is at \
10.42.1.2/24. Plug-and-play across all three OSes with adb shell available \
as a recovery channel even when networking is broken."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://bridgething-usb-gadget.sh \
    file://bridgething-usb-gadget.service \
    file://11-usb-rndis.network \
    file://12-usb-ecm.network \
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
    install -m 0644 ${S}/11-usb-rndis.network ${D}${sysconfdir}/systemd/network/
    install -m 0644 ${S}/12-usb-ecm.network   ${D}${sysconfdir}/systemd/network/

    # Drop-in retargets the upstream android-tools-adbd.service to our
    # composite gadget instead of its default adbd-only one. Ships
    # alongside this recipe because the file paths and unit naming are
    # tightly coupled to bridgething-usb-gadget's gadget tree.
    install -d ${D}${systemd_system_unitdir}/android-tools-adbd.service.d
    install -m 0644 ${S}/adbd-bridgething.conf \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service.d/bridgething.conf

    # ConditionPathExists gate for android-tools-adbd. Shipping the file
    # means adbd starts at boot; remove it on the device to disable.
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/usb-debugging-enabled ${D}${sysconfdir}/usb-debugging-enabled
}

FILES:${PN} = "\
    ${libexecdir}/bridgething-usb-gadget \
    ${systemd_system_unitdir}/bridgething-usb-gadget.service \
    ${systemd_system_unitdir}/android-tools-adbd.service.d/bridgething.conf \
    ${sysconfdir}/systemd/network/11-usb-rndis.network \
    ${sysconfdir}/systemd/network/12-usb-ecm.network \
    ${sysconfdir}/usb-debugging-enabled \
"

# adbd is the userspace half of the gadget's ADB function and the
# adbd-bridgething.conf drop-in lives under its unit dir, so RDEPEND on
# it directly rather than relying on packagegroup ordering.
RDEPENDS:${PN} = "bash android-tools-adbd"
