SUMMARY = "u-boot saved-environment image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://uboot-env.txt"

S = "${UNPACKDIR}"

DEPENDS = "u-boot-tools-native dosfstools-native mtools-native"

inherit deploy nopackages

UBOOT_ENV_SIZE ?= "0x10000"
UENV_IMG_KIB = "${@int(d.getVar('SUPERBIRD_ENV_PART_SIZE')) * 1024}"

do_compile() {
    mkenvimage -s ${UBOOT_ENV_SIZE} -o ${B}/uboot.env ${UNPACKDIR}/uboot-env.txt
    rm -f ${B}/superbird-uenv.vfat
    dd if=/dev/zero of=${B}/superbird-uenv.vfat bs=1024 count=${UENV_IMG_KIB}
    mkfs.vfat -n ENV ${B}/superbird-uenv.vfat
    mcopy -i ${B}/superbird-uenv.vfat ${B}/uboot.env ::uboot.env
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/superbird-uenv.vfat ${DEPLOYDIR}/superbird-uenv.vfat
    install -m 0644 ${B}/uboot.env ${DEPLOYDIR}/uboot.env
}
addtask deploy after do_compile before do_build
