SUMMARY = "boot_X FAT image (kernel + dtb + extlinux) for OTA"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

inherit deploy nopackages

DEPENDS = "dosfstools-native mtools-native"

# must equal the boot_X --fixed-size in superbird-mainline.wks.
BOOT_IMG_KIB ?= "32768"

do_compile[depends] += " \
    virtual/kernel:do_deploy \
    superbird-extlinux:do_deploy \
"

do_compile() {
    rm -f ${B}/boot.vfat
    dd if=/dev/zero of=${B}/boot.vfat bs=1024 count=${BOOT_IMG_KIB}
    mkfs.vfat -n BOOT ${B}/boot.vfat
    mmd -i ${B}/boot.vfat ::extlinux
    mcopy -i ${B}/boot.vfat ${DEPLOY_DIR_IMAGE}/Image.gz ::extlinux/Image.gz
    mcopy -i ${B}/boot.vfat ${DEPLOY_DIR_IMAGE}/meson-g12a-superbird.dtb ::extlinux/meson-g12a-superbird.dtb
    mcopy -i ${B}/boot.vfat ${DEPLOY_DIR_IMAGE}/extlinux.conf ::extlinux/extlinux.conf
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/boot.vfat ${DEPLOYDIR}/boot.vfat
}
addtask deploy after do_compile before do_build
