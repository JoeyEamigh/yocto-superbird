SUMMARY = "Auto-launch chromium as the bridgething kiosk under weston"
DESCRIPTION = "Systemd unit + launcher that exec chromium --kiosk on the running weston session. CDP on 9222. Target URL and flags from /etc/default/bridgething-kiosk."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-chromium-kiosk.service \
    file://bridgething-chromium-launch \
    file://bridgething-kiosk.env \
"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "chromium-ozone-wayland bridgething-weston-init"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-chromium-kiosk.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-chromium-kiosk.service \
        ${D}${systemd_system_unitdir}/bridgething-chromium-kiosk.service

    install -d ${D}${bindir}
    install -m 0755 ${S}/bridgething-chromium-launch \
        ${D}${bindir}/bridgething-chromium-launch

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${S}/bridgething-kiosk.env \
        ${D}${sysconfdir}/default/bridgething-kiosk
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/bridgething-chromium-kiosk.service \
    ${bindir}/bridgething-chromium-launch \
    ${sysconfdir}/default/bridgething-kiosk \
"

# conffile so on-target edits survive package upgrades
CONFFILES:${PN} = "${sysconfdir}/default/bridgething-kiosk"
