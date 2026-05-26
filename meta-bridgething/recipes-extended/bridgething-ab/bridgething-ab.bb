SUMMARY = "A/B slot debug helper"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://bridgething-ab.sh"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "libubootenv-bin"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/bridgething-ab.sh ${D}${bindir}/bridgething-ab
}

FILES:${PN} = "${bindir}/bridgething-ab"
