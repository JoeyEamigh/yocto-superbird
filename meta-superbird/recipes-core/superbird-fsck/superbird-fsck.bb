SUMMARY = "Pre-mount partition check and repair"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-fsck.sh \
    file://superbird-fsck@.service \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-fsck@bandaid.service superbird-fsck@data.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "e2fsprogs-e2fsck e2fsprogs-mke2fs e2fsprogs-tune2fs"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/superbird-fsck.sh ${D}${libexecdir}/superbird-fsck

    sed -i \
        -e "s|@@JOURNAL_MIB@@|${SUPERBIRD_EXT4_JOURNAL_SIZE}|g" \
        ${D}${libexecdir}/superbird-fsck

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/superbird-fsck@.service \
        ${D}${systemd_system_unitdir}/superbird-fsck@.service
}

do_install[vardeps] += "SUPERBIRD_EXT4_JOURNAL_SIZE"

FILES:${PN} = " \
    ${libexecdir}/superbird-fsck \
    ${systemd_system_unitdir}/superbird-fsck@.service \
"
