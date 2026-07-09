SUMMARY = "boot_X FAT image (kernel + dtb + extlinux) for OTA"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

inherit deploy nopackages

DEPENDS = "dosfstools-native mtools-native zchunk-native"

BOOT_IMG_KIB = "${@int(d.getVar('SUPERBIRD_BOOT_PART_SIZE')) * 1024}"

do_compile[depends] += " \
    virtual/kernel:do_deploy \
    superbird-extlinux:do_deploy \
"

do_compile() {
    rm -f ${B}/boot.vfat
    dd if=/dev/zero of=${B}/boot.vfat bs=1024 count=${BOOT_IMG_KIB}
    mkfs.vfat -n BOOT ${B}/boot.vfat
    mmd -i ${B}/boot.vfat ::extlinux
    mcopy -i ${B}/boot.vfat ${DEPLOY_DIR_IMAGE}/Image ::extlinux/Image
    mcopy -i ${B}/boot.vfat ${DEPLOY_DIR_IMAGE}/meson-g12a-superbird.dtb ::extlinux/meson-g12a-superbird.dtb
    mcopy -i ${B}/boot.vfat ${DEPLOY_DIR_IMAGE}/extlinux.conf ::extlinux/extlinux.conf

    rm -f ${B}/boot.vfat.zck ${B}/boot.vfat.zck.zckheader
    zck --output ${B}/boot.vfat.zck -u --chunk-hash-type sha256 ${B}/boot.vfat
    hdr_size=$(zck_read_header -v ${B}/boot.vfat.zck | grep 'Header size' | cut -d ':' -f 2 | tr -d '[:space:]')
    dd if=${B}/boot.vfat.zck of=${B}/boot.vfat.zck.zckheader count=1 bs=$hdr_size
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/boot.vfat ${DEPLOYDIR}/boot.vfat
    install -m 0644 ${B}/boot.vfat.zck ${DEPLOYDIR}/boot.vfat.zck
    install -m 0644 ${B}/boot.vfat.zck.zckheader ${DEPLOYDIR}/boot.vfat.zck.zckheader
}
addtask deploy after do_compile before do_build
