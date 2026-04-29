SUMMARY = "Minimal Panfrost GPU smoke test - EGL surfaceless + GLES2 triangle"
DESCRIPTION = "Opens an EGL_PLATFORM_SURFACELESS_MESA context, renders a \
cyan triangle on an orange background into a 64x64 FBO, prints GL strings \
and reads back center/corner pixels. Binary-level proof that Panfrost +\
Mesa userspace stack accelerates anything."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://gltest.c"
S = "${UNPACKDIR}"

DEPENDS = "virtual/egl virtual/libgles2"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o bridgething-gltest gltest.c -lEGL -lGLESv2
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 bridgething-gltest ${D}${bindir}/
}

FILES:${PN} = "${bindir}/bridgething-gltest"
