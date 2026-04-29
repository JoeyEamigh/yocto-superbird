SUMMARY = "Bridgething swupdate runtime config"
DESCRIPTION = "/etc/swupdate.cfg pointing the uboot-env handler at \
our /etc/fw_env.config (which superbird-base-files ships)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://swupdate.cfg"
S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/swupdate.cfg ${D}${sysconfdir}/swupdate.cfg
}

FILES:${PN} = "${sysconfdir}/swupdate.cfg"

RDEPENDS:${PN} = "swupdate libubootenv-bin"
