SUMMARY = "Bridgething daemon"
DESCRIPTION = "Bridgething Rust daemon plus the /opt overlay launcher, first-boot seeder, and rollback service."
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit cargo systemd pkgconfig

# gitsm:// pulls the swupdate-sys vendored submodule; bindgen reads its headers at build time
SRC_URI = "gitsm://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP} \
           file://bridgething.service \
           file://bridgething.conf \
           file://bridgething-launch \
           file://bridgething-opt-init \
           file://bridgething-opt-init.service \
           file://bridgething-rollback \
           file://bridgething-rollback.service \
           file://bridgething-dev.conf"
SRCREV = "${AUTOREV}"

# cargo fetches crates.io directly during do_compile. lift the network ban, stop bitbake
# vendoring redirect, and drop --frozen (which implies --offline). --locked keeps Cargo.lock authoritative.
do_compile[network] = "1"
CARGO_DISABLE_BITBAKE_VENDORING = "1"
CARGO_BUILD_FLAGS:remove = "--frozen"

# only the daemon from the workspace; superbird feature gates systemd + ALS + mic
CARGO_BUILD_FLAGS:append = " -p bridgething --no-default-features --features superbird --locked"

DEPENDS = "dbus swupdate systemd clang-native alsa-lib"

# bindgen runs on the build host; point libclang at the cross sysroot or it picks up host glibc headers
export LIBCLANG_PATH = "${STAGING_LIBDIR_NATIVE}"
export BINDGEN_EXTRA_CLANG_ARGS = "--sysroot=${RECIPE_SYSROOT}"

# auto_generate_cdp would shell out to rustfmt, which isn't in the recipe sysroot
export DO_NOT_FORMAT = "1"

# rollback unit is OnFailure-pulled, never enabled
SYSTEMD_SERVICE:${PN} = "bridgething.service bridgething-opt-init.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} += "bridgething-stock-webapp bridgething-opt-overlay swupdate systemd"

# default cargo_do_install drops every workspace binary into /usr/bin; we want libexec + a wrapper
do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${B}/target/${CARGO_TARGET_SUBDIR}/bridgething \
        ${D}${libexecdir}/bridgething
    install -m 0755 ${UNPACKDIR}/bridgething-opt-init \
        ${D}${libexecdir}/bridgething-opt-init
    install -m 0755 ${UNPACKDIR}/bridgething-rollback \
        ${D}${libexecdir}/bridgething-rollback

    install -d ${D}${bindir}
    install -m 0755 ${UNPACKDIR}/bridgething-launch ${D}${bindir}/bridgething

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/bridgething.service \
        ${D}${systemd_system_unitdir}/bridgething.service
    install -m 0644 ${UNPACKDIR}/bridgething-opt-init.service \
        ${D}${systemd_system_unitdir}/bridgething-opt-init.service
    install -m 0644 ${UNPACKDIR}/bridgething-rollback.service \
        ${D}${systemd_system_unitdir}/bridgething-rollback.service

    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${UNPACKDIR}/bridgething.conf \
        ${D}${nonarch_libdir}/tmpfiles.d/bridgething.conf

    install -d ${D}${systemd_system_unitdir}/bridgething.service.d
    install -m 0644 ${UNPACKDIR}/bridgething-dev.conf \
        ${D}${systemd_system_unitdir}/bridgething.service.d/dev.conf
}

# dev-only drop-in lives in its own subpackage so prod never gets trace logging
PACKAGES =+ "${PN}-dev-config"

FILES:${PN} = " \
    ${libexecdir}/bridgething \
    ${libexecdir}/bridgething-opt-init \
    ${libexecdir}/bridgething-rollback \
    ${bindir}/bridgething \
    ${systemd_system_unitdir}/bridgething.service \
    ${systemd_system_unitdir}/bridgething-opt-init.service \
    ${systemd_system_unitdir}/bridgething-rollback.service \
    ${nonarch_libdir}/tmpfiles.d/bridgething.conf \
"

FILES:${PN}-dev-config = "${systemd_system_unitdir}/bridgething.service.d/dev.conf"
RDEPENDS:${PN}-dev-config = "${PN}"
SUMMARY:${PN}-dev-config = "Bridgething daemon dev drop-in"

# upstream Cargo.toml strips the binary; honor that instead of forcing yocto's strip pass
INSANE_SKIP:${PN} += "already-stripped"
