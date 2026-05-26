SUMMARY = "Minimal Panfrost GPU smoke test"
DESCRIPTION = "Opens an EGL surfaceless context, renders a triangle to a 64x64 FBO, and reads pixels back to confirm Panfrost + Mesa accelerate."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://gltest.c"
S = "${UNPACKDIR}"

DEPENDS = "virtual/egl virtual/libgles2"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o superbird-gltest gltest.c -lEGL -lGLESv2
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 superbird-gltest ${D}${bindir}/
}

FILES:${PN} = "${bindir}/superbird-gltest"
