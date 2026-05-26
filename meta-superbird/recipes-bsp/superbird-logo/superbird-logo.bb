SUMMARY = "Superbird boot logo BMP"
DESCRIPTION = "Deploys the raw boot logo BMP that wic packs into boot_a/b vfat as /logo.bmp. u-boot's carthing_show_splash overpaints the baked-in logo with this during the video probe."
LICENSE = "CLOSED"

# override SUPERBIRD_BOOT_LOGO_NAME via bbappend to drop a custom bootup BMP
SUPERBIRD_BOOT_LOGO_NAME ?= "bootup.bmp"

SRC_URI = "file://${SUPERBIRD_BOOT_LOGO_NAME}"

S = "${UNPACKDIR}"

inherit deploy

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${S}/${SUPERBIRD_BOOT_LOGO_NAME} ${DEPLOYDIR}/logo.bmp
}
addtask deploy after do_compile before do_build

do_compile[noexec] = "1"
do_install[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
