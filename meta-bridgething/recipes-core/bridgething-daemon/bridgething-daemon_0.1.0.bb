SUMMARY = "Bridgething daemon (Rust workspace, superbird feature)"
DESCRIPTION = "Builds the bridgething Rust workspace from source and ships \
the daemon at /usr/libexec/bridgething. /usr/bin/bridgething is a thin \
wrapper that prefers the dev-overlay path /opt/bridgething/daemon/bridgething \
when present (so push-daemon iteration keeps working) and falls back to the \
in-rootfs binary otherwise. Also installs the systemd Type=notify unit and \
tmpfiles config that creates /var/{,lib/}bridgething/{webapps,state}."
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit cargo systemd pkgconfig

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP} \
           file://bridgething.service \
           file://bridgething.conf \
           file://bridgething-launch \
           file://bridgething-dev.conf"
SRCREV = "${AUTOREV}"

# Cargo fetches crates.io directly during do_compile. Trades full crate-mirror
# reproducibility for ergonomic recipe maintenance: bumping SRCREV (or running
# with AUTOREV) re-resolves Cargo.lock without regenerating a hand-tracked
# ${BPN}-crates.inc via cargo-bitbake. Three knobs are needed:
#   1. do_compile[network] = "1" - lift bitbake's network-task ban
#   2. CARGO_DISABLE_BITBAKE_VENDORING = "1" - stop cargo_common.bbclass from
#      redirecting source.crates-io to the empty ${CARGO_VENDORING_DIRECTORY}
#   3. drop --frozen from CARGO_BUILD_FLAGS - --frozen implies --offline, so
#      cargo would refuse network even after the previous two; --locked alone
#      keeps the committed Cargo.lock authoritative
do_compile[network] = "1"
CARGO_DISABLE_BITBAKE_VENDORING = "1"
CARGO_BUILD_FLAGS:remove = "--frozen"

# Build only the daemon binary from the workspace. Without -p, cargo
# would compile every workspace member (bridgething-mfi-proxy,
# tools/codegen, packages/adapter-node, ...). The default feature set
# enables `chrome` (host-side CDP hosting); on the device we want the
# `superbird` feature instead, which is systemd-aware and pulls ALS +
# mic. --locked keeps Cargo.lock authoritative now that --frozen is
# gone (--frozen also implied --offline, hence the :remove above).
CARGO_BUILD_FLAGS:append = " -p bridgething --no-default-features --features superbird --locked"

# bluer pulls libdbus-sys, which links against libdbus-1 on the target.
DEPENDS = "dbus swupdate systemd"

# headless_chrome's transitive build dep auto_generate_cdp shells out to
# rustfmt to pretty-print the generated CDP bindings. rust-native doesn't
# stage rustfmt into the recipe sysroot (only target rust does), so set the
# crate's documented opt-out env var instead of pulling in a rustfmt-native
# layer override - the formatting is cosmetic, not load-bearing.
export DO_NOT_FORMAT = "1"

SYSTEMD_SERVICE:${PN} = "bridgething.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} += "bridgething-stock-webapp"

# Override cargo.bbclass's default cargo_do_install — that one drops every
# workspace binary into /usr/bin. We want the real binary at /usr/libexec
# and the wrapper script at /usr/bin/bridgething.
do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${B}/target/${CARGO_TARGET_SUBDIR}/bridgething \
        ${D}${libexecdir}/bridgething

    install -d ${D}${bindir}
    install -m 0755 ${UNPACKDIR}/bridgething-launch ${D}${bindir}/bridgething

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/bridgething.service \
        ${D}${systemd_system_unitdir}/bridgething.service

    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${UNPACKDIR}/bridgething.conf \
        ${D}${nonarch_libdir}/tmpfiles.d/bridgething.conf

    install -d ${D}${systemd_system_unitdir}/bridgething.service.d
    install -m 0644 ${UNPACKDIR}/bridgething-dev.conf \
        ${D}${systemd_system_unitdir}/bridgething.service.d/dev.conf
}

# Split the dev-only drop-in into its own sub-package so the prod image
# never gets trace logging or UART log mirroring. packagegroup-bridgething-dev
# RDEPENDS this package, which pulls in ${PN} as well.
PACKAGES =+ "${PN}-dev-config"

FILES:${PN} = " \
    ${libexecdir}/bridgething \
    ${bindir}/bridgething \
    ${systemd_system_unitdir}/bridgething.service \
    ${nonarch_libdir}/tmpfiles.d/bridgething.conf \
"

FILES:${PN}-dev-config = "${systemd_system_unitdir}/bridgething.service.d/dev.conf"
RDEPENDS:${PN}-dev-config = "${PN}"
SUMMARY:${PN}-dev-config = "Dev-image drop-in: trace RUST_LOG + log mirror to /dev/console"

# Cargo.toml's [profile.release] has strip = "symbols", so the binary lands
# already stripped. Yocto's QA flags this because it would normally split
# debug symbols off into a -dbg package; honor the upstream strip choice
# instead of forcing strip = "none" + duplicating Yocto's strip pass.
INSANE_SKIP:${PN} += "already-stripped"
