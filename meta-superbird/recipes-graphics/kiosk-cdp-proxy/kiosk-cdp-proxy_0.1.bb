SUMMARY = "DevTools reverse proxy for the chromium kiosk"
DESCRIPTION = "Rewrites the Host header to the connecting interface address so chromium's loopback-bound CDP endpoint is reachable by hostname, and so the WebSocket URLs it advertises point back at a routable address."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://kiosk-cdp-proxy \
    file://kiosk-cdp-proxy.service \
    file://kiosk-cdp-proxy.env \
"

S = "${UNPACKDIR}/kiosk-cdp-proxy"

inherit cargo systemd

SYSTEMD_SERVICE:${PN} = "kiosk-cdp-proxy.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/kiosk-cdp-proxy.service \
        ${D}${systemd_system_unitdir}/kiosk-cdp-proxy.service

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${UNPACKDIR}/kiosk-cdp-proxy.env \
        ${D}${sysconfdir}/default/kiosk-cdp-proxy
}

FILES:${PN} += "${sysconfdir}/default/kiosk-cdp-proxy"

# conffile so on-target edits survive package upgrades
CONFFILES:${PN} = "${sysconfdir}/default/kiosk-cdp-proxy"
