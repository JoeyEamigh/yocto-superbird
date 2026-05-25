SUMMARY = "Bridgething MFi auth-chip dev proxy"
DESCRIPTION = "TCP server exposing /dev/i2c-3 (MFi coprocessor) so the dev host can drive the chip from cargo tests."
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit cargo systemd

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP} \
           file://bridgething-mfi-proxy.service"
SRCREV = "${AUTOREV}"

do_compile[network] = "1"
CARGO_DISABLE_BITBAKE_VENDORING = "1"
CARGO_BUILD_FLAGS:remove = "--frozen"

# scope cargo to mfi-proxy so the daemon's heavy deps don't pull in
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

# upstream Cargo.toml strips the binary; honor that
INSANE_SKIP:${PN} += "already-stripped"
