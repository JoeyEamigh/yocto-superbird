SUMMARY = "Auto-launch chromium kiosk under weston"
DESCRIPTION = "Systemd unit + launcher that exec chromium --kiosk on the running weston session. CDP on 9223. Target URL via CHROMIUM_KIOSK_URL knob; runtime overrides via /etc/default/chromium-kiosk."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://chromium-kiosk.service \
    file://chromium-kiosk-launch \
    file://kiosk.env \
"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "chromium-ozone-wayland superbird-weston-init noto-color-emoji noto-sans-cjk"

inherit systemd

SYSTEMD_SERVICE:${PN} = "chromium-kiosk.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/chromium-kiosk.service \
        ${D}${systemd_system_unitdir}/chromium-kiosk.service

    install -d ${D}${bindir}
    install -m 0755 ${S}/chromium-kiosk-launch \
        ${D}${bindir}/chromium-kiosk-launch

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${S}/kiosk.env \
        ${D}${sysconfdir}/default/chromium-kiosk

    # bake distro knob defaults into the env file
    sed -i \
        -e 's|@@KIOSK_URL@@|${CHROMIUM_KIOSK_URL}|g' \
        -e 's|@@KIOSK_PROXY_SERVER@@|${CHROMIUM_KIOSK_PROXY_SERVER}|g' \
        ${D}${sysconfdir}/default/chromium-kiosk
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/chromium-kiosk.service \
    ${bindir}/chromium-kiosk-launch \
    ${sysconfdir}/default/chromium-kiosk \
"

# conffile so on-target edits survive package upgrades
CONFFILES:${PN} = "${sysconfdir}/default/chromium-kiosk"
