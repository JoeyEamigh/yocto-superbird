SUMMARY = "Bridgething OTA artifact (.swu) for the prod image - delta + raw"
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

# OTA offsets matching bridgething-prod-image's geometry.
# Production system_a / system_b are at the AML MPT positions
# (same on every prod variant, byte-identical to stock).
SUPERBIRD_OTA_BOOT_A_OFFSET   = "0xd600000"
SUPERBIRD_OTA_BOOT_B_OFFSET   = "0xee00000"
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
# without racing against the dev image's flashthing_zip - both
# images publish image-namespaced symlinks in DEPLOY_DIR_IMAGE,
# and our do_stage_swu_inputs task copies them into a subdir keyed
# to this recipe's name. swupdate-common's add_image_to_swu uses
# os.path.basename on SWUPDATE_IMAGES entries, so subdir-relative
# paths cpio under the bare name.
SWU_STAGEDIR = "bridgething-update-prod-stage"
SWUPDATE_IMAGES = "${SWU_STAGEDIR}/boot.img ${SWU_STAGEDIR}/system.img.zck.zckheader"

# Must match bridgething-prod-image.bb's SUPERBIRD_ROOTFS_TYPE - used
# in do_stage_swu_inputs to find the right
# `<link>.<rootfs_ext>.zck.zckheader` artifact in DEPLOY_DIR_IMAGE.
# Bitbake variables are recipe-scoped, so this can't inherit from the
# image recipe; keep both pinned to the same value.
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
# them up under the right cpio entry names. Avoids a race with the
# dev image's flashthing_zip rewriting the generic
# DEPLOY_DIR_IMAGE/system.img alias.
#
# SUPERBIRD_ROOTFS_TYPE picks the rootfs filesystem (squashfs-zst on
# prod). The zchunk-converted artifact is named
# `<link_name>.<ROOTFS_TYPE>.zck.zckheader` by Yocto's
# image_types_zchunk class, so we follow the same suffix here.
do_stage_swu_inputs() {
    set -eu
    stage="${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}"
    mkdir -p "$stage"
    rm -f "$stage/boot.img" "$stage/system.img.zck.zckheader"
    ln -s "../bridgething-prod-image-superbird.boot.img" \
        "$stage/boot.img"
    ln -s "../bridgething-prod-image-superbird.${SUPERBIRD_ROOTFS_TYPE}.zck.zckheader" \
        "$stage/system.img.zck.zckheader"
}
addtask stage_swu_inputs after do_unpack before do_swuimage
do_stage_swu_inputs[depends] += "bridgething-prod-image:do_flashthing_zip"

# Make sure the prod image's flashthing zip + boot.img + zck
# artifacts are in DEPLOY_DIR_IMAGE before we try to pack them.
do_swuimage[depends] += "bridgething-prod-image:do_flashthing_zip"
