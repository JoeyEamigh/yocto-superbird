SUMMARY = "Display handoff smoke test"
DESCRIPTION = "Mmap /dev/mem at u-boot's fb_addr and overwrite the splash with an RGB/W band pattern to verify bootloader handoff."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

SRC_URI = "file://superbird-fbpaint.c \
           file://superbird-fbpaint.service"
S = "${UNPACKDIR}"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o superbird-fbpaint superbird-fbpaint.c
}

do_install() {
    install -d ${D}${libexecdir} ${D}${systemd_system_unitdir}
    install -m 0755 superbird-fbpaint ${D}${libexecdir}/superbird-fbpaint
    install -m 0644 superbird-fbpaint.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = "${libexecdir}/superbird-fbpaint ${systemd_system_unitdir}/superbird-fbpaint.service"

inherit systemd
SYSTEMD_SERVICE:${PN} = "superbird-fbpaint.service"
SYSTEMD_AUTO_ENABLE = "disable"
