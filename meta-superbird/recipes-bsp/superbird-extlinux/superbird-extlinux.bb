SUMMARY = "extlinux.conf for the Car Thing mainline-u-boot boot path"
DESCRIPTION = "Ships the extlinux config that u-boot sysboot parses to \
boot the kernel. Deployed to DEPLOY_DIR_IMAGE; the machine IMAGE_BOOT_FILES \
places it on the boot_a FAT partition under /extlinux/."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://extlinux.conf"

S = "${UNPACKDIR}"

inherit deploy nopackages

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${UNPACKDIR}/extlinux.conf ${DEPLOYDIR}/extlinux.conf
}
addtask deploy after do_unpack before do_build
