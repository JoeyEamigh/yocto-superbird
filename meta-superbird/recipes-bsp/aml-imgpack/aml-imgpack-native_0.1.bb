SUMMARY = "Resource packer/unpacker for Amlogic Logo image files"
DESCRIPTION = "Single-file Python tool that reads/writes Amlogic's \
AmlResImg container format (used for the logo partition's BMP \
splash assets). Used at build time to assemble logo.img from BMP \
sources."
HOMEPAGE = "https://github.com/bishopdynamics/aml-imgpack"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = "file://aml-imgpack.py"

S = "${UNPACKDIR}"

inherit native

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/aml-imgpack.py ${D}${bindir}/aml-imgpack
}
