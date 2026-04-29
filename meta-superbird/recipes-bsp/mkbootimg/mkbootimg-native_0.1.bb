SUMMARY = "Tiny Android boot.img v0 packer for Superbird boot_a/b"
DESCRIPTION = "Single-file Python tool that produces a v0 boot.img with \
the page size and load addresses the stock Amlogic u-boot (v1.0-74) \
expects to read from boot_a/b via `imgread kernel`. Lives as a -native \
helper for the flashthing image class."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://mkbootimg.py"

S = "${UNPACKDIR}"

inherit native

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/mkbootimg.py ${D}${bindir}/mkbootimg-superbird
}
