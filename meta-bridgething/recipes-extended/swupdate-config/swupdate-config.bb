SUMMARY = "Bridgething swupdate runtime config"
DESCRIPTION = "/etc/swupdate.cfg pointing the uboot-env handler at \
our /etc/fw_env.config (which superbird-base-files ships), plus a \
swupdate.sh conf.d drop-in that enables the (stable, slot_a|slot_b) \
selectors via -a so IPC clients can request them."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://swupdate.cfg \
    file://10-bridgething-allowed-selections \
"
S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/swupdate.cfg ${D}${sysconfdir}/swupdate.cfg

    install -d ${D}${libdir}/swupdate/conf.d
    install -m 0644 ${S}/10-bridgething-allowed-selections \
        ${D}${libdir}/swupdate/conf.d/10-bridgething-allowed-selections
}

FILES:${PN} = " \
    ${sysconfdir}/swupdate.cfg \
    ${libdir}/swupdate/conf.d/10-bridgething-allowed-selections \
"

RDEPENDS:${PN} = "swupdate libubootenv-bin"
