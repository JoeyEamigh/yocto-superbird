SUMMARY = "Rotary encoder → wheel-scroll virtual mouse"
DESCRIPTION = "Tiny C daemon that grabs the kernel rotary-encoder evdev \
node and forwards REL_HWHEEL events through a uinput virtual mouse with \
the full pointer capability set declared. libinput classifies the \
uinput device as a real mouse, so wayland routes the events as standard \
horizontal scroll - webapps see plain wheel events with no cooperation \
needed. Cursor never moves; X/Y are declared but never emitted."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-rotary.c \
    file://bridgething-rotary.service \
    file://bridgething-rotary.conf \
    file://99-bridgething-rotary.rules \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-rotary.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o bridgething-rotary ${S}/bridgething-rotary.c
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 bridgething-rotary ${D}${bindir}/bridgething-rotary

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-rotary.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}
    install -m 0644 ${S}/bridgething-rotary.conf ${D}${sysconfdir}/bridgething-rotary.conf

    install -d ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${S}/99-bridgething-rotary.rules \
        ${D}${sysconfdir}/udev/rules.d/
}

FILES:${PN} = " \
    ${bindir}/bridgething-rotary \
    ${systemd_system_unitdir}/bridgething-rotary.service \
    ${sysconfdir}/bridgething-rotary.conf \
    ${sysconfdir}/udev/rules.d/99-bridgething-rotary.rules \
"

CONFFILES:${PN} = "${sysconfdir}/bridgething-rotary.conf"
