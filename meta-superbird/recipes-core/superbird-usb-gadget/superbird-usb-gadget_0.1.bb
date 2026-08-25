SUMMARY = "USB gadget bring-up: composite CDC-NCM + FunctionFS-ADB"
DESCRIPTION = "Single-config composite on the DWC2 controller. Per-serial /29 subnet and DHCP via systemd-networkd."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://superbird-usb-gadget.sh \
    file://superbird-usb-gadget.service \
    file://90-usb-ncm-fallback.network \
    file://adbd-superbird.conf \
    file://usb-debugging-enabled \
    file://adb-getprop \
    file://adb-wm \
    file://adb-dumpsys \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-usb-gadget.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${S}/superbird-usb-gadget.sh ${D}${libexecdir}/superbird-usb-gadget

    sed -i \
        -e 's|@@USB_GADGET_NAME@@|${SUPERBIRD_USB_GADGET_NAME}|g' \
        -e 's|@@USB_MANUFACTURER@@|${SUPERBIRD_USB_GADGET_MANUFACTURER}|g' \
        -e 's|@@USB_PRODUCT@@|${SUPERBIRD_USB_GADGET_PRODUCT}|g' \
        -e 's|@@HOSTNAME@@|${SUPERBIRD_HOSTNAME}|g' \
        ${D}${libexecdir}/superbird-usb-gadget

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-usb-gadget.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${S}/90-usb-ncm-fallback.network ${D}${sysconfdir}/systemd/network/

    # drop-in retargets android-tools-adbd at the composite gadget
    install -d ${D}${systemd_system_unitdir}/android-tools-adbd.service.d
    install -m 0644 ${S}/adbd-superbird.conf \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service.d/superbird.conf
    sed -i \
        -e 's|@@USB_GADGET_NAME@@|${SUPERBIRD_USB_GADGET_NAME}|g' \
        ${D}${systemd_system_unitdir}/android-tools-adbd.service.d/superbird.conf

    install -d ${D}${bindir}
    install -m 0755 ${S}/adb-getprop ${D}${bindir}/getprop
    install -m 0755 ${S}/adb-wm      ${D}${bindir}/wm
    install -m 0755 ${S}/adb-dumpsys ${D}${bindir}/dumpsys
    sed -i \
        -e 's|@@USB_PRODUCT@@|${SUPERBIRD_USB_GADGET_PRODUCT}|g' \
        -e 's|@@USB_MANUFACTURER@@|${SUPERBIRD_USB_GADGET_MANUFACTURER}|g' \
        -e 's|@@HOSTNAME@@|${SUPERBIRD_HOSTNAME}|g' \
        ${D}${bindir}/getprop

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
    ${bindir}/getprop \
    ${bindir}/wm \
    ${bindir}/dumpsys \
"

RDEPENDS:${PN} = "bash android-tools-adbd"
