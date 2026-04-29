SUMMARY = "Display handoff smoke test - paint 4-band pattern at u-boot fb_addr"
DESCRIPTION = "After kernel boot, mmap /dev/mem at 0x1f800000 (u-boot's \
framebuffer) and overwrite the splash with a horizontal red/green/blue/white \
test pattern. If the bands show on the panel, the bootloader-handoff display \
patches worked end to end (panel still alive, OSD plane still scanning the \
bootloader fb, DSI/encoder/CRTC config preserved)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

SRC_URI = "file://bridgething-fbpaint.c \
           file://bridgething-fbpaint.service"
S = "${UNPACKDIR}"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o bridgething-fbpaint bridgething-fbpaint.c
}

do_install() {
    install -d ${D}${libexecdir} ${D}${systemd_system_unitdir}
    install -m 0755 bridgething-fbpaint ${D}${libexecdir}/bridgething-fbpaint
    install -m 0644 bridgething-fbpaint.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = "${libexecdir}/bridgething-fbpaint ${systemd_system_unitdir}/bridgething-fbpaint.service"

inherit systemd
SYSTEMD_SERVICE:${PN} = "bridgething-fbpaint.service"
SYSTEMD_AUTO_ENABLE = "disable"
