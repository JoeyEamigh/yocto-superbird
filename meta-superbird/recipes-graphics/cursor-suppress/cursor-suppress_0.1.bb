SUMMARY = "Weston module that suppresses cursor rendering"
DESCRIPTION = "Hooks weston_output's frame_signal to unmap any pointer sprite. ABI-pinned to libweston-15."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://cursor-suppress.c"
S = "${UNPACKDIR}"

DEPENDS = "weston wayland"
inherit pkgconfig

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -fPIC -shared \
        $(pkg-config --cflags --libs weston libweston-15) \
        -o cursor-suppress.so \
        ${S}/cursor-suppress.c
}

do_install() {
    install -d ${D}${libdir}/weston
    install -m 0755 cursor-suppress.so \
        ${D}${libdir}/weston/cursor-suppress.so
}

FILES:${PN} = "${libdir}/weston/cursor-suppress.so"

# libweston ABI is versioned; weston bumps need pkg-config + rebuild here
RDEPENDS:${PN} = "weston"
