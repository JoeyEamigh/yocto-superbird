SUMMARY = "Superbird boot/burn-mode logo partition image"
DESCRIPTION = "Packs BMP splash assets into an AmlResImg container (logo.img). BMPs must be 480x800 RGB565 to match the env.txt DSI init."
LICENSE = "CLOSED"

# override SUPERBIRD_BOOT_LOGO_NAME via bbappend to drop a custom bootup BMP
SUPERBIRD_BOOT_LOGO_NAME ?= "bootup.bmp"

SRC_URI = " \
    file://${SUPERBIRD_BOOT_LOGO_NAME} \
    file://burn_mode.bmp \
    file://bad_charger.bmp \
    file://overheat.bmp \
    file://shell_mode.bmp \
"

S = "${UNPACKDIR}"
# separate B avoids the self-copy guard when SUPERBIRD_BOOT_LOGO_NAME is bootup.bmp
B = "${WORKDIR}/build"

DEPENDS = "aml-imgpack-native"

inherit deploy

do_compile() {
    install -d ${B}
    # aml-imgpack uses the filename as the asset name; force 'bootup' regardless of source
    install -m 0644 ${S}/${SUPERBIRD_BOOT_LOGO_NAME} ${B}/bootup.bmp
    install -m 0644 ${S}/burn_mode.bmp ${B}/burn_mode.bmp
    install -m 0644 ${S}/bad_charger.bmp ${B}/bad_charger.bmp

    cd ${B}
    aml-imgpack --pack ${B}/logo.img \
        bootup.bmp \
        burn_mode.bmp \
        bad_charger.bmp
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/logo.img ${DEPLOYDIR}/logo.img
}
addtask deploy after do_compile before do_build

# deploy-only; not a rootfs package
do_install[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
