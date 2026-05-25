SUMMARY = "Amlogic AmlResImg packer/unpacker"
DESCRIPTION = "Single-file Python tool that assembles logo.img BMPs into Amlogic's AmlResImg container."
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
