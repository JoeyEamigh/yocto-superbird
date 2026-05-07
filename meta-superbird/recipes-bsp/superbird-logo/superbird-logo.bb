SUMMARY = "Superbird boot/burn-mode logo partition image"
DESCRIPTION = "Packs BMP splash assets into an Amlogic AmlResImg \
container (logo.img) for flashing to the logo partition. The \
u-boot env's init_display + do_usb_burning sequences reference \
asset names from this image (bootup, burn_mode, bad_charger) - \
keep names in sync with env.txt's imgread calls. \
\
The bootup splash is application-customizable: bbappend in a \
downstream layer to override SUPERBIRD_BOOT_LOGO_NAME with a \
different file dropped via FILESEXTRAPATHS. The asset name baked \
into logo.img stays 'bootup' regardless of source filename. BMPs \
must be 480x800 16-bit (RGB565), matching the panel's 16bpp DSI \
init in env.txt; magick the source with \
'-rotate 90 -define bmp:subtype=RGB565'."
LICENSE = "CLOSED"

# Default boot splash. Override via bbappend in an application layer
# to drop in a different BMP without touching this recipe.
SUPERBIRD_BOOT_LOGO_NAME ?= "bootup.bmp"

SRC_URI = " \
    file://${SUPERBIRD_BOOT_LOGO_NAME} \
    file://burn_mode.bmp \
    file://bad_charger.bmp \
    file://overheat.bmp \
    file://shell_mode.bmp \
"

S = "${UNPACKDIR}"
# Default B is S in this recipe shape, which trips the do_compile
# self-copy guard when SUPERBIRD_BOOT_LOGO_NAME is 'bootup.bmp'.
# Carve out a separate build dir so source and destination never alias.
B = "${WORKDIR}/build"

DEPENDS = "aml-imgpack-native"

inherit deploy

do_compile() {
    install -d ${B}
    # aml-imgpack uses the input filename (without .bmp) as the asset
    # name in the AmlResImg container; copy the variable-named source
    # to a fixed 'bootup.bmp' so 'imgread pic logo bootup' resolves
    # regardless of which file the bbappend supplied.
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

# No rootfs install - logo.img is a deploy-only flash input.
do_install[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
