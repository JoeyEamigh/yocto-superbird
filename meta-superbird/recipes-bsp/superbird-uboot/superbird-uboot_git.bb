SUMMARY = "Mainline u-boot (BL33) + signed boot.bin for the Car Thing"
HOMEPAGE = "https://github.com/ThingLabsOSS/superbird-uboot"

require recipes-bsp/u-boot/u-boot-common.inc
require recipes-bsp/u-boot/u-boot.inc

PV = "2026.07-rc2+git${SRCPV}"

SRCREV = "158f191b27df1956e397754f5a66c4ae28873aef"
SRC_URI = "git://github.com/ThingLabsOSS/superbird-uboot.git;protocol=https;branch=master"

DEPENDS += "bc-native dtc-native gnutls-native python3-pyelftools-native"

UBOOT_MACHINE = "spotify_carthing_defconfig"

COMPATIBLE_MACHINE = "^superbird$"

DEPENDS += "superbird-fip-tools-native"

SUPERBIRD_BOOT_IMAGE ?= "superbird-boot.bin"

do_deploy:append() {
    fip-tool sign \
        -k ${STAGING_DATADIR_NATIVE}/superbird-fip-tools/keys/aml-user-key.sig \
        -o ${B}/fip-out \
        ${B}/u-boot.bin

    fip-tool flash ours \
        --stock-bootloader ${STAGING_DATADIR_NATIVE}/superbird-fip-tools/stock.bootloader.bin \
        --signed-fip ${B}/fip-out/u-boot.bin.spotify.encrypt \
        -o ${DEPLOYDIR}/${SUPERBIRD_BOOT_IMAGE} --dry-run

    install -m 0644 ${B}/fip-out/u-boot.bin.spotify.encrypt \
        ${DEPLOYDIR}/u-boot.bin.spotify.encrypt
}
