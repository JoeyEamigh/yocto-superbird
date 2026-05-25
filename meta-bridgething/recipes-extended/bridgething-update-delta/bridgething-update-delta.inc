DESCRIPTION = "swupdate .swu shipping boot.img verbatim and system.img as a zchunk delta. delta handler fetches missing chunks via the loopback range proxy and chains to the raw handler at the inactive slot offset."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

BRIDGETHING_OTA_SOURCE_IMAGE ??= ""

python __anonymous() {
    if not d.getVar('BRIDGETHING_OTA_SOURCE_IMAGE'):
        bb.fatal("BRIDGETHING_OTA_SOURCE_IMAGE must be set by the wrapping recipe")
}

BRIDGETHING_OTA_SOURCE_LINKNAME = "${BRIDGETHING_OTA_SOURCE_IMAGE}-${MACHINE}"

# stock aml mpt geometry. dtbo_X carries the raw board dtb u-boot loads at fdt_addr.
SUPERBIRD_OTA_BOOT_A_OFFSET   = "0xd600000"
SUPERBIRD_OTA_BOOT_B_OFFSET   = "0xee00000"
SUPERBIRD_OTA_DTBO_A_OFFSET   = "0xac00000"
SUPERBIRD_OTA_DTBO_B_OFFSET   = "0xb800000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"

# delta source is the slot opposite the target (bridgething-ab writes to inactive)
SUPERBIRD_OTA_SLOT_A_SOURCE = "/dev/mmcblk0p2"
SUPERBIRD_OTA_SLOT_B_SOURCE = "/dev/mmcblk0p1"

# loopback range proxy bound by the bridgething daemon; forwarded as OtaAssetRange to the companion
SUPERBIRD_OTA_DELTA_URL_BASE ?= "http://127.0.0.1:8893"

# per-recipe staging subdir so cpio entries use bare names without colliding with the source image
SWU_STAGEDIR = "${PN}-stage"
SWUPDATE_IMAGES = "${SWU_STAGEDIR}/boot.img ${SWU_STAGEDIR}/dtb ${SWU_STAGEDIR}/system.img.zck.zckheader"

# must match the source image's SUPERBIRD_ROOTFS_TYPE; bbvars don't cross recipe scope
SUPERBIRD_ROOTFS_TYPE = "squashfs-lz4"

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
        -e "s|@@SLOT_A_SOURCE@@|${SUPERBIRD_OTA_SLOT_A_SOURCE}|g" \
        -e "s|@@SLOT_B_SOURCE@@|${SUPERBIRD_OTA_SLOT_B_SOURCE}|g" \
        -e "s|@@DELTA_URL_BASE@@|${SUPERBIRD_OTA_DELTA_URL_BASE}|g" \
        ${UNPACKDIR}/sw-description
}
addtask render_sw_description after do_unpack before do_swuimage

# bare-named symlinks under the stage dir so SWUPDATE_IMAGES cpios under the right entry names
do_stage_swu_inputs() {
    set -eu
    stage="${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}"
    mkdir -p "$stage"
    rm -f "$stage/boot.img" "$stage/dtb" "$stage/system.img.zck.zckheader"
    ln -s "../${BRIDGETHING_OTA_SOURCE_LINKNAME}.boot.img" \
        "$stage/boot.img"
    ln -s "../${BRIDGETHING_OTA_SOURCE_LINKNAME}.dtb" \
        "$stage/dtb"
    ln -s "../${BRIDGETHING_OTA_SOURCE_LINKNAME}.${SUPERBIRD_ROOTFS_TYPE}.zck.zckheader" \
        "$stage/system.img.zck.zckheader"
}
addtask stage_swu_inputs after do_unpack before do_swuimage
do_stage_swu_inputs[depends] += "${BRIDGETHING_OTA_SOURCE_IMAGE}:do_flashthing_zip"
do_swuimage[depends] += "${BRIDGETHING_OTA_SOURCE_IMAGE}:do_flashthing_zip"
