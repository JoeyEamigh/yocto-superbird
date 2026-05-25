SUMMARY = "Apply a .swu to the inactive A/B slot via swupdate-client"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://superbird-ota.sh"
S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/superbird-ota.sh ${D}${bindir}/superbird-ota
}

RDEPENDS:${PN} = "swupdate swupdate-client swupdate-config libubootenv-bin"
