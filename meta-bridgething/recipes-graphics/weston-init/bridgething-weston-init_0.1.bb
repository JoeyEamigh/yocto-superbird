SUMMARY = "Boot-weston systemd unit + weston.ini variants"
DESCRIPTION = "Starts Weston bound to DSI-1 480x800 rotated. Ships -desktop and -kiosk weston.ini subpackages and the wsh wrapper for Wayland-aware spawned clients."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-weston.service \
    file://weston.ini \
    file://weston-kiosk.ini \
    file://bridgething-splash.png \
    file://bridgething-weston-dev.env \
    file://wsh \
"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "weston dbus blank-cursor"

# image recipes pick -desktop or -kiosk; RCONFLICTS forbids both at once
PACKAGES =+ "${PN}-desktop ${PN}-kiosk"

RDEPENDS:${PN}-desktop   = "${PN}"
RDEPENDS:${PN}-kiosk = "${PN}"
RCONFLICTS:${PN}-desktop   = "${PN}-kiosk"
RCONFLICTS:${PN}-kiosk = "${PN}-desktop"

inherit systemd

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "bridgething-weston.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-weston.service ${D}${systemd_system_unitdir}/

    install -d ${D}${bindir}
    install -m 0755 ${S}/wsh ${D}${bindir}/wsh

    install -d ${D}${datadir}/bridgething
    install -m 0644 ${S}/bridgething-splash.png ${D}${datadir}/bridgething/

    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/weston.ini       ${D}${sysconfdir}/weston-dev.ini
    install -m 0644 ${S}/weston-kiosk.ini ${D}${sysconfdir}/weston-kiosk.ini

    # dev env file is staged here; -desktop's postinst copies it into /etc/default
    install -d ${D}${datadir}/bridgething
    install -m 0644 ${S}/bridgething-weston-dev.env \
        ${D}${datadir}/bridgething/bridgething-weston-dev.env
}

pkg_postinst:${PN}-desktop() {
    ln -sf weston-dev.ini $D${sysconfdir}/weston.ini
    install -d $D${sysconfdir}/default
    if [ ! -e $D${sysconfdir}/default/bridgething-weston ]; then
        install -m 0644 $D${datadir}/bridgething/bridgething-weston-dev.env \
            $D${sysconfdir}/default/bridgething-weston
    fi
}

pkg_postinst:${PN}-kiosk() {
    ln -sf weston-kiosk.ini $D${sysconfdir}/weston.ini
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/bridgething-weston.service \
    ${bindir}/wsh \
    ${datadir}/bridgething/bridgething-splash.png \
    ${datadir}/bridgething/bridgething-weston-dev.env \
    ${sysconfdir}/weston-dev.ini \
    ${sysconfdir}/weston-kiosk.ini \
"

ALLOW_EMPTY:${PN}-desktop   = "1"
ALLOW_EMPTY:${PN}-kiosk = "1"
FILES:${PN}-desktop   = ""
FILES:${PN}-kiosk = ""
