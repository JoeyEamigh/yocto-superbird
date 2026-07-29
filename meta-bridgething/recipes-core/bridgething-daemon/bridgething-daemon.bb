SUMMARY = "Bridgething daemon"
DESCRIPTION = "Bridgething Rust daemon"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require bridgething-daemon-pin.inc

PV = "${BRIDGETHING_DAEMON_VERSION}"

BRIDGETHING_DAEMON_PROVISION ?= "prebuilt"
BRIDGETHING_OTA_BASE ?= "https://ota.bridgething.com"

DAEMON_URI = "${BRIDGETHING_OTA_BASE}/daemon/stable/${PV}/bridgething;name=daemon;downloadfilename=bridgething-${PV};unpack=0"
DAEMON_GIT_URI = "gitsm://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"

FROM_SOURCE = "${@'1' if d.getVar('BRIDGETHING_DAEMON_PROVISION') == 'source' else '0'}"

inherit systemd pkgconfig
inherit ${@'cargo' if d.getVar('BRIDGETHING_DAEMON_PROVISION') == 'source' else ''}

SRC_URI = " \
    ${@d.getVar('DAEMON_GIT_URI') if d.getVar('FROM_SOURCE') == '1' else d.getVar('DAEMON_URI')} \
    file://bridgething.service \
    file://bridgething-stock.socket \
    file://bridgething-modern.socket \
    file://bridgething.conf \
    file://bridgething-rollback \
    file://bridgething-rollback.service \
    file://bridgething-adopt-daemon \
    file://bridgething-dev.conf \
"

SRC_URI[daemon.sha256sum] = "${BRIDGETHING_DAEMON_SHA256}"

SRCREV = "${AUTOREV}"

do_compile[network] = "1"
CARGO_DISABLE_BITBAKE_VENDORING = "1"
CARGO_BUILD_FLAGS:remove = "--frozen"

CARGO_BUILD_FLAGS:append = " -p bridgething --no-default-features --features superbird --locked"

DEPENDS = "dbus swupdate systemd alsa-lib"
DEPENDS:append = "${@' clang-native' if d.getVar('FROM_SOURCE') == '1' else ''}"

export LIBCLANG_PATH = "${STAGING_LIBDIR_NATIVE}"
export BINDGEN_EXTRA_CLANG_ARGS = "--sysroot=${RECIPE_SYSROOT}"

export DO_NOT_FORMAT = "1"

SYSTEMD_SERVICE:${PN} = "bridgething.service bridgething-stock.socket bridgething-modern.socket"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} += "opt-overlay swupdate systemd"

DAEMON_FLOOR_DIR = "${nonarch_libdir}/bridgething/daemon"

OPT_OVERLAY_TARGET = "/opt/bridgething"

DAEMON_BINARY = "${@d.getVar('B') + '/target/' + d.getVar('CARGO_TARGET_SUBDIR') + '/bridgething' if d.getVar('FROM_SOURCE') == '1' else d.getVar('UNPACKDIR') + '/bridgething-' + d.getVar('PV')}"

python () {
    if d.getVar('FROM_SOURCE') == '1':
        return
    d.delVar('SRCREV')
    for task in ('do_configure', 'do_compile'):
        d.setVarFlag(task, 'noexec', '1')
}

do_install() {
    install -d ${D}${OPT_OVERLAY_TARGET}

    install -d ${D}${DAEMON_FLOOR_DIR}
    install -m 0755 ${DAEMON_BINARY} ${D}${DAEMON_FLOOR_DIR}/bridgething.current

    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/bridgething-rollback \
        ${D}${libexecdir}/bridgething-rollback
    install -m 0755 ${UNPACKDIR}/bridgething-adopt-daemon \
        ${D}${libexecdir}/bridgething-adopt-daemon

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/bridgething.service \
        ${D}${systemd_system_unitdir}/bridgething.service
    install -m 0644 ${UNPACKDIR}/bridgething-rollback.service \
        ${D}${systemd_system_unitdir}/bridgething-rollback.service
    install -m 0644 ${UNPACKDIR}/bridgething-stock.socket \
        ${D}${systemd_system_unitdir}/bridgething-stock.socket
    install -m 0644 ${UNPACKDIR}/bridgething-modern.socket \
        ${D}${systemd_system_unitdir}/bridgething-modern.socket

    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${UNPACKDIR}/bridgething.conf \
        ${D}${nonarch_libdir}/tmpfiles.d/bridgething.conf

    install -d ${D}${systemd_system_unitdir}/bridgething.service.d
    install -m 0644 ${UNPACKDIR}/bridgething-dev.conf \
        ${D}${systemd_system_unitdir}/bridgething.service.d/dev.conf
}

PACKAGES =+ "${PN}-dev-config"

FILES:${PN} = " \
    ${OPT_OVERLAY_TARGET} \
    ${DAEMON_FLOOR_DIR}/bridgething.current \
    ${libexecdir}/bridgething-rollback \
    ${libexecdir}/bridgething-adopt-daemon \
    ${systemd_system_unitdir}/bridgething.service \
    ${systemd_system_unitdir}/bridgething-rollback.service \
    ${systemd_system_unitdir}/bridgething-stock.socket \
    ${systemd_system_unitdir}/bridgething-modern.socket \
    ${nonarch_libdir}/tmpfiles.d/bridgething.conf \
"

FILES:${PN}-dev-config = "${systemd_system_unitdir}/bridgething.service.d/dev.conf"
RDEPENDS:${PN}-dev-config = "${PN}"
SUMMARY:${PN}-dev-config = "Bridgething daemon dev drop-in"

INSANE_SKIP:${PN} += "already-stripped"
