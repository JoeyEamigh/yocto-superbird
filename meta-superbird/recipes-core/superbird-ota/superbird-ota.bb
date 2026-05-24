SUMMARY = "Apply a mainline .swu to the inactive A/B slot via swupdate"
DESCRIPTION = "Thin device-side wrapper: reads slot_active, picks the inactive \
slot, and drives swupdate-client into it. The .swu's bootenv block owns the \
slot_active flip + try-counter reset, so this script holds no boot policy."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://superbird-ota.sh"
S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/superbird-ota.sh ${D}${bindir}/superbird-ota
}

# swupdate-client is a separate package from swupdate (the IPC client binary the
# wrapper drives); swupdate-config carries swupdate.cfg + the slot select allowlist.
RDEPENDS:${PN} = "swupdate swupdate-client swupdate-config libubootenv-bin"
