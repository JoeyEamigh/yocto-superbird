SUMMARY = "Weston module that suppresses cursor rendering"
DESCRIPTION = "Tiny weston plugin loaded via [core] modules= in \
weston.ini. Hooks every weston_output's frame_signal and unmaps any \
pointer's sprite view, so the rotary encoder can stay exposed to \
libinput as a real pointer (REL_HWHEEL → wl_pointer.axis) while no \
cursor pixel is ever drawn. ABI-pinned to libweston-15."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://bridgething-cursor-suppress.c"
S = "${UNPACKDIR}"

DEPENDS = "weston wayland"
inherit pkgconfig

# Weston modules live in ${libdir}/weston alongside the shells
# (kiosk-shell.so, fullscreen-shell.so, hmi-controller.so, etc.). The
# .so is loaded by name from there when weston.ini lists it under
# [core] modules=.
do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -fPIC -shared \
        $(pkg-config --cflags --libs weston libweston-15) \
        -o bridgething-cursor-suppress.so \
        ${S}/bridgething-cursor-suppress.c
}

do_install() {
    install -d ${D}${libdir}/weston
    install -m 0755 bridgething-cursor-suppress.so \
        ${D}${libdir}/weston/bridgething-cursor-suppress.so
}

FILES:${PN} = "${libdir}/weston/bridgething-cursor-suppress.so"

# libweston ABI is versioned: weston bumps require a rebuild of this
# module against the new headers and update of the pkg-config name.
RDEPENDS:${PN} = "weston"
