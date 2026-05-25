SUMMARY = "Bridgething raw rootfs OTA"
DESCRIPTION = "swupdate .swu shipping boot.img and the full rootfs blob for A/B OTA streamed by bridgething-ab."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# stock aml mpt geometry. dtbo_X carries the raw board dtb u-boot loads at fdt_addr.
SUPERBIRD_OTA_BOOT_A_OFFSET   = "0xd600000"
SUPERBIRD_OTA_BOOT_B_OFFSET   = "0xee00000"
SUPERBIRD_OTA_DTBO_A_OFFSET   = "0xac00000"
SUPERBIRD_OTA_DTBO_B_OFFSET   = "0xb800000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"

# bare names resolve via the superbird-flashthing symlinks; pipeline is fstype-agnostic
SWUPDATE_IMAGES = "boot.img dtb system.img"

SWUPDATE_SIGNING ??= ""

inherit swupdate

SRC_URI = " \
    file://sw-description \
"

# build datetime feeds the swu version so the daemon can refuse stale replays
SWU_VERSION = "${DISTRO_VERSION}+${DATETIME}"
SWU_VERSION[vardepsexclude] = "DATETIME"

# do_swuimage is a python task, so use a separate shell task to fill placeholders first
do_render_sw_description() {
    sed -i \
        -e "s|@@VERSION@@|${SWU_VERSION}|g" \
        -e "s|@@BOOT_A_OFFSET@@|${SUPERBIRD_OTA_BOOT_A_OFFSET}|g" \
        -e "s|@@BOOT_B_OFFSET@@|${SUPERBIRD_OTA_BOOT_B_OFFSET}|g" \
        -e "s|@@DTBO_A_OFFSET@@|${SUPERBIRD_OTA_DTBO_A_OFFSET}|g" \
        -e "s|@@DTBO_B_OFFSET@@|${SUPERBIRD_OTA_DTBO_B_OFFSET}|g" \
        -e "s|@@SYSTEM_A_OFFSET@@|${SUPERBIRD_OTA_SYSTEM_A_OFFSET}|g" \
        -e "s|@@SYSTEM_B_OFFSET@@|${SUPERBIRD_OTA_SYSTEM_B_OFFSET}|g" \
        ${UNPACKDIR}/sw-description
}
addtask render_sw_description after do_unpack before do_swuimage

do_swuimage[depends] += "bridgething-dev-image:do_flashthing_zip"
