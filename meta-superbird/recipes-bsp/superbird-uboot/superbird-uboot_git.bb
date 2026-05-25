SUMMARY = "Mainline u-boot (BL33) + signed flashable boot.bin for the Car Thing"
DESCRIPTION = "Builds the ThingLabsOSS/superbird-uboot fork (mainline u-boot \
2026.07 with the Superbird board port + the ab_boot A/B slot selector) as \
BL33, packs it into a g12a FIP signed with the public spotify aml-user-key.sig, \
and pairs the FIP body with the stock BL2 to produce superbird-boot.bin -- the \
cold-bootable 2 MiB image for the eMMC boot0/boot1 hardware partitions. \
The raw u-boot.bin and the signed u-boot.bin.spotify.encrypt are deployed \
alongside for reference / mask-ROM USB dev loading."
HOMEPAGE = "https://github.com/ThingLabsOSS/superbird-uboot"

# Reuse oe-core's u-boot build machinery (toolchain env, host deps, deploy)
# but point it at our fork. u-boot-common.inc's LIC_FILES_CHKSUM
# (Licenses/README) matches the fork byte-for-byte.
require recipes-bsp/u-boot/u-boot-common.inc
require recipes-bsp/u-boot/u-boot.inc

# Fork tracks 2026.07; the +gitSRCPV keeps PV monotonic across SRCREV bumps.
PV = "2026.07-rc2+git${SRCPV}"

# ThingLabsOSS fork. Overrides the denx SRC_URI/SRCREV from u-boot-common.inc.
SRCREV = "c645900efcf75779475e3b041a16e555bfdc0e87"
SRC_URI = "git://github.com/ThingLabsOSS/superbird-uboot.git;protocol=https;branch=master"

# Host build deps, matching oe-core's u-boot_2026.01 (same era as the fork).
DEPENDS += "bc-native dtc-native gnutls-native python3-pyelftools-native"

# The Car Thing board defconfig (configs/spotify_carthing_defconfig).
UBOOT_MACHINE = "spotify_carthing_defconfig"

COMPATIBLE_MACHINE = "^superbird$"

# --- FIP sign + stock-BL2 pairing ---------------------------------------
# Done in do_deploy:append using the native fip-tool, after u-boot.inc has
# built + deployed the raw u-boot.bin (at ${B}/u-boot.bin, no UBOOT_CONFIG).
DEPENDS += "superbird-fip-tools-native"

SUPERBIRD_BOOT_IMAGE ?= "superbird-boot.bin"

do_deploy:append() {
    # [1] BL33 -> g12a FIP, assembled + Spotify-signed entirely in pure Go
    #     (fip-tool sign: embedded BL2/SCP/DDR prefix + TF-A 2.14 BL31 + our
    #     u-boot as BL33, native signer). No aml_encrypt_g12a, no clone, no
    #     shell. fip-tool is on PATH via superbird-fip-tools-native.
    fip-tool sign \
        -k ${STAGING_DATADIR_NATIVE}/superbird-fip-tools/keys/aml-user-key.sig \
        -o ${B}/fip-out \
        ${B}/u-boot.bin

    # [2] Stock BL2 (in-repo stock.bootloader.bin) + our signed FIP body
    #     -> cold-bootable 2 MiB boot0/boot1 image. --dry-run assembles the
    #     image to -o without touching any USB device.
    fip-tool flash ours \
        --stock-bootloader ${STAGING_DATADIR_NATIVE}/superbird-fip-tools/stock.bootloader.bin \
        --signed-fip ${B}/fip-out/u-boot.bin.spotify.encrypt \
        -o ${DEPLOYDIR}/${SUPERBIRD_BOOT_IMAGE} --dry-run

    # Reference artifact next to the flashable image.
    install -m 0644 ${B}/fip-out/u-boot.bin.spotify.encrypt \
        ${DEPLOYDIR}/u-boot.bin.spotify.encrypt
}
