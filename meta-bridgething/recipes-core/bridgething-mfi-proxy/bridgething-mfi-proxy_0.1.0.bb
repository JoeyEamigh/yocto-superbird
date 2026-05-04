SUMMARY = "Bridgething MFi auth-chip dev proxy"
DESCRIPTION = "Tiny TCP server that exposes /dev/i2c-3 (the MFi \
authentication coprocessor bus) over the network so the dev host can \
drive the chip from `cargo test` against bridgething-mfi's `RemoteI2c` \
transport. Conflicts with bridgething.service (which owns /dev/i2c-3 \
during iAP2 auth + the backlight via the in-daemon ALS manager) and \
with bridgething-als.service (legacy fallback, disabled by default) \
since this proxy briefly blanks the backlight. Dev image only - this \
is a dev iteration tool, not a production component."
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit cargo systemd

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP} \
           file://bridgething-mfi-proxy.service"
SRCREV = "${AUTOREV}"

# Same network-build pattern as bridgething-daemon - cargo fetches
# crates.io at do_compile time rather than maintaining a hand-vendored
# crate manifest. See bridgething-daemon_0.1.0.bb for the rationale.
do_compile[network] = "1"
CARGO_DISABLE_BITBAKE_VENDORING = "1"
CARGO_BUILD_FLAGS:remove = "--frozen"

# Restrict cargo to mfi-proxy's transitive dep graph. Without -p, cargo
# would compile every workspace member - including the daemon, which
# pulls bluer + libdbus-sys + headless_chrome and is the bulk of the
# build. mfi-proxy itself only needs i2cdev / nix / tracing, so scoping
# here drops the recipe's compile time by an order of magnitude and
# removes the libdbus-1 link requirement.
CARGO_BUILD_FLAGS:append = " -p bridgething-mfi-proxy --locked"

SYSTEMD_SERVICE:${PN} = "bridgething-mfi-proxy.service"
SYSTEMD_AUTO_ENABLE = "disable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/target/${CARGO_TARGET_SUBDIR}/bridgething-mfi-proxy \
        ${D}${bindir}/bridgething-mfi-proxy

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/bridgething-mfi-proxy.service \
        ${D}${systemd_system_unitdir}/bridgething-mfi-proxy.service
}

FILES:${PN} = " \
    ${bindir}/bridgething-mfi-proxy \
    ${systemd_system_unitdir}/bridgething-mfi-proxy.service \
"

# Cargo's profile.release does the strip; honor the upstream choice
# rather than splitting symbols into a -dbg package we don't use.
INSANE_SKIP:${PN} += "already-stripped"
