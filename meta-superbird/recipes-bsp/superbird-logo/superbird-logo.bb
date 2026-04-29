SUMMARY = "Superbird boot/burn-mode logo partition image"
DESCRIPTION = "Packs BMP splash assets into an Amlogic AmlResImg \
container (logo.img) for flashing to the logo partition. The \
u-boot env's init_display + do_usb_burning sequences reference \
asset names from this image (bootup_spotify, burn_mode, \
bad_charger) - keep names in sync with env.txt's imgread calls."
LICENSE = "CLOSED"

SRC_URI = " \
    file://bootup_spotify.bmp \
    file://burn_mode.bmp \
    file://bad_charger.bmp \
    file://overheat.bmp \
    file://shell_mode.bmp \
"

S = "${UNPACKDIR}"

DEPENDS = "aml-imgpack-native"

inherit deploy

do_compile() {
    cd ${S}
    aml-imgpack --pack ${B}/logo.img \
        bootup_spotify.bmp \
        burn_mode.bmp \
        bad_charger.bmp
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/logo.img ${DEPLOYDIR}/logo.img
}
addtask deploy after do_compile before do_build

# No rootfs install - logo.img is a deploy-only flash input.
do_install[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
