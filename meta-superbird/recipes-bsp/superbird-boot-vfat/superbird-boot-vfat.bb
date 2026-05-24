SUMMARY = "boot_X FAT image for the mainline A/B layout (kernel + dtb + extlinux)"
DESCRIPTION = "The 32 MiB FAT image that backs a boot_a/boot_b partition: \
Image.gz + the board DTB + extlinux.conf under /extlinux/. The wic builds this \
in-tree via bootimg-partition for the factory image; this standalone copy is what \
an OTA writes raw to the inactive slot's boot partition. Content is slot-independent \
(extlinux resolves root=PARTLABEL=root_${slot} at boot via the u-boot slot var)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

inherit deploy nopackages

DEPENDS = "dosfstools-native mtools-native"

# Must equal the boot_a/boot_b --fixed-size in superbird-mainline.wks.
BOOT_IMG_KIB ?= "32768"

# Same artifacts the wic's IMAGE_BOOT_FILES copies onto boot_a.
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
