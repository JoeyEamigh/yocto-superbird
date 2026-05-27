SUMMARY = "Minimal example daemon for the kiosk image"
DESCRIPTION = "Hello-world systemd daemon. Demonstrates the bandaid + opt-overlay chain end-to-end; binary lives at /usr/lib/superbird-kiosk/daemon/kioskd.current in the rootfs floor and on the bandaid partition (rebased to /superbird-kiosk/daemon/). Replace with your own daemon when forking."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://kioskd.c \
    file://superbird-kiosk-daemon.service \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-kiosk-daemon.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} += "opt-overlay"

DAEMON_FLOOR_DIR = "${nonarch_libdir}/superbird-kiosk/daemon"
OPT_OVERLAY_TARGET = "/opt/superbird-kiosk"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -O2 -o ${B}/kioskd ${S}/kioskd.c
}

# rootfs floor: /opt/superbird-kiosk is the bind target (squashfs is ro,
# so the empty dir must exist at install time). daemon binary lives at
# /usr/lib/superbird-kiosk/daemon/ so bandaid's vendor rebase finds it.
do_install() {
    install -d ${D}${OPT_OVERLAY_TARGET}

    install -d ${D}${DAEMON_FLOOR_DIR}
    install -m 0755 ${B}/kioskd ${D}${DAEMON_FLOOR_DIR}/kioskd.current

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-kiosk-daemon.service \
        ${D}${systemd_system_unitdir}/superbird-kiosk-daemon.service
}

FILES:${PN} = " \
    ${OPT_OVERLAY_TARGET} \
    ${DAEMON_FLOOR_DIR}/kioskd.current \
    ${systemd_system_unitdir}/superbird-kiosk-daemon.service \
"
