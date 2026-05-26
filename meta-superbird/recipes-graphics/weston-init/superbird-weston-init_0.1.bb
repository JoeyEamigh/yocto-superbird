SUMMARY = "Boot-weston systemd unit + weston.ini variants"
DESCRIPTION = "Starts weston bound to DSI-1 480x800 rotated. Ships -desktop and -kiosk weston.ini subpackages and the wsh wrapper for spawned wayland clients."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-weston.service \
    file://weston.ini \
    file://weston-kiosk.ini \
    file://${SUPERBIRD_WESTON_SPLASH_IMAGE} \
    file://weston-dev.env \
    file://wsh \
"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "weston dbus blank-cursor"

# image recipes pick -desktop or -kiosk; RCONFLICTS forbids both at once
PACKAGES =+ "${PN}-desktop ${PN}-kiosk"

RDEPENDS:${PN}-desktop = "${PN}"
RDEPENDS:${PN}-kiosk   = "${PN}"
RCONFLICTS:${PN}-desktop = "${PN}-kiosk"
RCONFLICTS:${PN}-kiosk   = "${PN}-desktop"

inherit systemd

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "superbird-weston.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-weston.service ${D}${systemd_system_unitdir}/

    install -d ${D}${bindir}
    install -m 0755 ${S}/wsh ${D}${bindir}/wsh

    install -d ${D}${datadir}/superbird
    install -m 0644 ${S}/${SUPERBIRD_WESTON_SPLASH_IMAGE} ${D}${datadir}/superbird/splash.png

    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/weston.ini       ${D}${sysconfdir}/weston-dev.ini
    install -m 0644 ${S}/weston-kiosk.ini ${D}${sysconfdir}/weston-kiosk.ini

    # dev env file is staged here; -desktop's postinst copies it into /etc/default
    install -m 0644 ${S}/weston-dev.env ${D}${datadir}/superbird/weston-dev.env
}

pkg_postinst:${PN}-desktop() {
    ln -sf weston-dev.ini $D${sysconfdir}/weston.ini
    install -d $D${sysconfdir}/default
    if [ ! -e $D${sysconfdir}/default/weston ]; then
        install -m 0644 $D${datadir}/superbird/weston-dev.env \
            $D${sysconfdir}/default/weston
    fi
}

pkg_postinst:${PN}-kiosk() {
    ln -sf weston-kiosk.ini $D${sysconfdir}/weston.ini
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/superbird-weston.service \
    ${bindir}/wsh \
    ${datadir}/superbird/splash.png \
    ${datadir}/superbird/weston-dev.env \
    ${sysconfdir}/weston-dev.ini \
    ${sysconfdir}/weston-kiosk.ini \
"

ALLOW_EMPTY:${PN}-desktop = "1"
ALLOW_EMPTY:${PN}-kiosk   = "1"
FILES:${PN}-desktop = ""
FILES:${PN}-kiosk   = ""
