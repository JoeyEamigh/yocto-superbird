SUMMARY = "Auto-launch chromium as the bridgething kiosk under weston"
DESCRIPTION = "Ships the systemd unit + launcher script that exec chromium \
in --kiosk mode bound to the running weston session, with CDP \
unconditionally on 0.0.0.0:9222 (LAN-debug-anywhere; the only network \
on this device is the USB-CDC-ECM gadget) and chrome's first paint \
matched to the bootloader splash so the boot transition is seamless. \
Target URL + extra chrome flags are sourced from \
/etc/default/bridgething-kiosk so iteration doesn't require an image \
rebuild."
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

# Treat /etc/default/bridgething-kiosk as a config file so on-target
# edits aren't clobbered by package upgrades during swupdate.
CONFFILES:${PN} = "${sysconfdir}/default/bridgething-kiosk"
