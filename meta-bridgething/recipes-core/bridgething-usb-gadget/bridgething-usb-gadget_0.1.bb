SUMMARY = "Bridgething USB gadget bring-up (CDC-NCM + ADB) for dev-iteration SSH/ADB"
DESCRIPTION = "Configures a single-config composite USB-gadget on the \
Superbird's DWC2 peripheral controller at boot. The configuration carries \
two functions: CDC-NCM (Linux/macOS/Win 8.1+ inbox, frame-aggregating so \
throughput beats both RNDIS and CDC-ECM) and FunctionFS-backed ADB (adbd \
writes the descriptors before the UDC bind). The boot script derives a \
/29 subnet from the device serial-sha (so two Car Things on one host \
land in disjoint subnets); the device IP is `10.42.1.<offset+2>` and \
systemd-networkd hands out DHCP leases inside the same /29. The avahi \
service published from the bridgething image announces `bridgething.local` \
(auto-suffixed when two devices race). Plug-and-play across all three \
OSes with adb shell available as a recovery channel even when networking \
is broken."
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
    ${sysconfdir}/systemd/network/11-usb-ncm.network \
    ${sysconfdir}/usb-debugging-enabled \
"

# adbd is the userspace half of the gadget's ADB function and the
# adbd-bridgething.conf drop-in lives under its unit dir, so RDEPEND on
# it directly rather than relying on packagegroup ordering.
RDEPENDS:${PN} = "bash android-tools-adbd"
