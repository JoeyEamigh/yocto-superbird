SUMMARY = "USB gadget bring-up: composite CDC-NCM + FunctionFS-ADB"
DESCRIPTION = "Single-config composite on the DWC2 controller. Per-serial /29 subnet, DHCP via systemd-networkd, transient mDNS hostname via avahi."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://superbird-usb-gadget.sh \
    file://superbird-usb-gadget.service \
    file://90-usb-ncm-fallback.network \
    file://adbd-superbird.conf \
    file://usb-debugging-enabled \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-usb-gadget.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${S}/superbird-usb-gadget.sh ${D}${libexecdir}/superbird-usb-gadget

    # bake distro knob values into the shell script (configfs paths + USB descriptor strings + hostname prefix).
    sed -i \
        -e 's|@@USB_GADGET_NAME@@|${SUPERBIRD_USB_GADGET_NAME}|g' \
        -e 's|@@USB_MANUFACTURER@@|${SUPERBIRD_USB_GADGET_MANUFACTURER}|g' \
        -e 's|@@USB_PRODUCT@@|${SUPERBIRD_USB_GADGET_PRODUCT}|g' \
        -e 's|@@HOSTNAME@@|${SUPERBIRD_HOSTNAME}|g' \
        ${D}${libexecdir}/superbird-usb-gadget

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-usb-gadget.service ${D}${systemd_system_unitdir}/

    # fallback applies only if the gadget script's per-serial /run/systemd/network/11-usb-ncm.network
    # was not generated (boot before the script ran, or the script was disabled). lower-numbered
    # files win first-match, so the per-serial 11- file wins when present, and this 90- file
    # provides a single-device default otherwise.
    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${S}/90-usb-ncm-fallback.network ${D}${sysconfdir}/systemd/network/

    # drop-in retargets android-tools-adbd at the composite gadget
    install -d ${D}${systemd_system_unitdir}/android-tools-adbd.service.d
    install -m 0644 ${S}/adbd-superbird.conf \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service.d/superbird.conf
    sed -i \
        -e 's|@@USB_GADGET_NAME@@|${SUPERBIRD_USB_GADGET_NAME}|g' \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service.d/superbird.conf

    # ConditionPathExists gate; remove the file on-device to disable adbd at boot
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/usb-debugging-enabled ${D}${sysconfdir}/usb-debugging-enabled
}

FILES:${PN} = "\
    ${libexecdir}/superbird-usb-gadget \
    ${systemd_system_unitdir}/superbird-usb-gadget.service \
    ${systemd_system_unitdir}/android-tools-adbd.service.d/superbird.conf \
    ${sysconfdir}/systemd/network/90-usb-ncm-fallback.network \
    ${sysconfdir}/usb-debugging-enabled \
"

RDEPENDS:${PN} = "bash android-tools-adbd"
