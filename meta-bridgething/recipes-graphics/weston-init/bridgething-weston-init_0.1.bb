SUMMARY = "Boot-weston systemd unit + weston.ini variants"
DESCRIPTION = "Starts the Weston DRM-backend compositor at boot bound to \
DSI-1 480x800@60 (rotated 270° for landscape). Ships two weston.ini \
variants in the -desktop and -kiosk subpackages; image recipes RDEPEND \
on exactly one. Both variants set cursor-theme=blank so weston picks \
up the fully-transparent xcursor theme shipped by blank-cursor.bb. \
Also ships /usr/bin/wsh — a wrapper that exports \
WAYLAND_DISPLAY/XDG_RUNTIME_DIR so spawned clients connect to the \
running compositor."
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

# blank-cursor ships the transparent xcursor theme that weston.ini
# references; pull it via RDEPENDS so weston-init can never land on a
# rootfs without the theme it asks for.
RDEPENDS:${PN} = "weston dbus blank-cursor"

# Per-image variant packages. RCONFLICTS prevents both from landing in
# the same image. Image recipes RDEPEND on -desktop or -kiosk, never
# the parent directly. Each variant's pkg_postinst creates the right
# /etc/weston.ini symlink.
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

    # The dev env file lands at /etc/default/bridgething-weston only
    # when -desktop is installed (created via postinst below). The .source
    # copy lives at a non-conflicting path so the parent can ship it.
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
