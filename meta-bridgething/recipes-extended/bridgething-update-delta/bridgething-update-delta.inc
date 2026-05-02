# Shared machinery for the zchunk-delta OTA .swu recipes.
# Wrappers (bridgething-update-prod.bb, bridgething-update-dev.bb)
# pin BRIDGETHING_OTA_SOURCE_IMAGE to the image their .swu deltas
# against. The source image must emit `squashfs-zst.zck` +
# `squashfs-zst.zck.zckheader` (IMAGE_FSTYPES += "..." +
# IMAGE_CLASSES += "image_types_zchunk").
#
# Output: <PN>-${MACHINE}.swu, packing boot.img verbatim (raw
# handler) and a zchunk header for system.img (delta handler chained
# to raw). The delta handler reads chunks from the currently-mounted
# slot, fetches missing chunks via HTTP from SUPERBIRD_OTA_DELTA_URL_BASE,
# reassembles, then writes to the inactive slot's byte offset on
# /dev/mmcblk0.

DESCRIPTION = "Generates a swupdate .swu archive that ships boot.img \
verbatim (raw handler) and system.img as a zchunk delta header \
(delta handler chained to raw). The delta handler reads chunks from \
the currently-mounted slot, fetches missing chunks via HTTP, \
reassembles, then hands the full image to the raw handler at the \
inactive slot's offset. The system.img blob is filesystem-agnostic - \
it follows SUPERBIRD_ROOTFS_TYPE (squashfs-zst on prod). \
\
The url= property points at http://127.0.0.1:NNN by default - the \
on-device bridgething daemon stands up a localhost HTTP-Range \
listener and bridges chunk requests over its existing RFCOMM \
transport to the gateway phone (which holds the .zck cache). For \
benchtop testing without the daemon, override SUPERBIRD_OTA_DELTA_URL_BASE \
to a host-side stub like http://10.42.1.1:8000."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Wrapping recipes pin this. Hard error if a future caller forgets.
BRIDGETHING_OTA_SOURCE_IMAGE ??= ""

python __anonymous() {
    if not d.getVar('BRIDGETHING_OTA_SOURCE_IMAGE'):
        bb.fatal("BRIDGETHING_OTA_SOURCE_IMAGE must be set by the wrapping recipe (e.g. bridgething-prod-image)")
}

# Yocto's default IMAGE_LINK_NAME is `${IMAGE_BASENAME}-${MACHINE}`.
# We mirror that here to find the source image's deploy symlinks
# without re-deriving from its bbvars (which we can't read across
# recipe scope anyway).
BRIDGETHING_OTA_SOURCE_LINKNAME = "${BRIDGETHING_OTA_SOURCE_IMAGE}-${MACHINE}"

# OTA offsets matching the stock AML MPT geometry. Both dev + prod
# images share this layout - squashfs makes the lean shape enough
# for the kitchen-sink dev install too. dtbo_a / dtbo_b carry the
# raw board DTB that u-boot loads at fdt_addr (boot.img's
# second-stage DTB is ignored), so a kernel update without a
# matching dtbo update would leave the new kernel parsing the old
# DTB.
SUPERBIRD_OTA_BOOT_A_OFFSET   = "0xd600000"
SUPERBIRD_OTA_BOOT_B_OFFSET   = "0xee00000"
SUPERBIRD_OTA_DTBO_A_OFFSET   = "0xac00000"
SUPERBIRD_OTA_DTBO_B_OFFSET   = "0xb800000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"

# Source partitions for delta - always the slot OPPOSITE the target,
# because bridgething-ab always writes to the inactive slot. The
# delta handler reads `source` to find chunks already on-disk.
SUPERBIRD_OTA_SLOT_A_SOURCE = "/dev/mmcblk0p2"
SUPERBIRD_OTA_SLOT_B_SOURCE = "/dev/mmcblk0p1"

# Where the device fetches the full .zck from. Default points at
# the host side of the USB-gadget link so benchtop iteration with
# `superbird-delta-stub.py` works out of the box. Will flip to a
# localhost target (the bridgething daemon's HTTP-Range bridge,
# which forwards over RFCOMM to the gateway phone) once the daemon
# side is in place and we know the shape of the URL contract.
SUPERBIRD_OTA_DELTA_URL_BASE ?= "http://10.42.1.1:8000"

# Per-recipe staging subdir in DEPLOY_DIR_IMAGE. Lets us use the
# bare cpio-entry names (`boot.img`, `system.img.zck.zckheader`)
# without racing against the source image's flashthing_zip - both
# the image and this recipe publish symlinks in DEPLOY_DIR_IMAGE,
# and our do_stage_swu_inputs task copies them into a subdir keyed
# to this recipe's PN. swupdate-common's add_image_to_swu uses
# os.path.basename on SWUPDATE_IMAGES entries, so subdir-relative
# paths cpio under the bare name.
SWU_STAGEDIR = "${PN}-stage"
SWUPDATE_IMAGES = "${SWU_STAGEDIR}/boot.img ${SWU_STAGEDIR}/dtb ${SWU_STAGEDIR}/system.img.zck.zckheader"

# Must match the source image's SUPERBIRD_ROOTFS_TYPE. bbvars are
# recipe-scoped so this can't inherit from the image; keep both
# pinned to squashfs-zst.
SUPERBIRD_ROOTFS_TYPE = "squashfs-zst"

# No signing for v1. Bridgething's gateway transport is the only
# delivery channel and runs over an authenticated bluetooth pair.
SWUPDATE_SIGNING ??= ""

inherit swupdate

SRC_URI = " \
    file://sw-description \
"

# Render @@VERSION@@ in sw-description from DISTRO_VERSION + the
# build datetime, so the daemon can refuse stale .swu replays.
SWU_VERSION = "${DISTRO_VERSION}+${DATETIME}"
SWU_VERSION[vardepsexclude] = "DATETIME"

# do_swuimage is a python task (decorated `python` in the bbclass),
# so we can't prepend a shell function to it. Use a separate shell
# task to fill placeholders before swupdate-common reads the
# manifest.
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

# Materialize bare-named symlinks under
# ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}/ so SWUPDATE_IMAGES picks
# them up under the right cpio entry names. The link names follow
# Yocto's IMAGE_LINK_NAME convention, mirrored in
# BRIDGETHING_OTA_SOURCE_LINKNAME above.
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

# Make sure the source image's flashthing zip + boot.img + zck
# artifacts are in DEPLOY_DIR_IMAGE before we try to pack them.
do_swuimage[depends] += "${BRIDGETHING_OTA_SOURCE_IMAGE}:do_flashthing_zip"
