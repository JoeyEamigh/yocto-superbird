SUMMARY = "Default placeholder webapp for the kiosk example image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://index.html"
S = "${UNPACKDIR}"

inherit allarch

do_install() {
    install -d ${D}${datadir}/superbird-kiosk-default
    install -m 0644 ${S}/index.html ${D}${datadir}/superbird-kiosk-default/index.html
}

FILES:${PN} = "${datadir}/superbird-kiosk-default"
