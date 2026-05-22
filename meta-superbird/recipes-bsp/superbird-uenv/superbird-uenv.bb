SUMMARY = "u-boot saved-environment image for the mainline-u-boot boot path"
DESCRIPTION = "Builds uboot.env via mkenvimage and wraps it in an 8 MiB \
FAT image. elle's mainline u-boot ships an intentionally policy-free \
default environment; this supplies the boot policy and is loaded as \
CONFIG_ENV_FAT_FILE from the GPT partition named env. Consumed by the \
env partition in superbird-mainline.wks."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://uboot-env.txt"

S = "${UNPACKDIR}"

DEPENDS = "u-boot-tools-native dosfstools-native mtools-native"

inherit deploy nopackages

# Matches the Car Thing u-boot CONFIG_ENV_SIZE.
UBOOT_ENV_SIZE ?= "0x10000"
# env partition size (KiB) - must equal the wks --fixed-size for env.
UENV_IMG_KIB ?= "8192"

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
