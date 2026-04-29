SUMMARY = "Bridgething OTA artifact (.swu) - raw rootfs delivery"
DESCRIPTION = "Generates a swupdate .swu archive with boot.img + the \
full rootfs blob that bridgething-ab streams to a running device for \
an A/B over-the-air update. The .swu embeds the sw-description \
manifest that picks the inactive slot at install time and flips \
u-boot's active_slot var on success. \
\
Sibling bridgething-update-prod.bb produces a zchunk *delta* OTA \
against the same image, used when minimizing bytes-on-wire matters \
(production transport is bluetooth). This raw recipe is the simpler \
path used during dev iteration where a 552 MB transfer over USB-CDC \
is fine. Both recipes share the same partition offsets (stock AML \
MPT) and the same image artifacts."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# OTA offsets — stock AML MPT geometry. Both dev + prod images share
# this layout now that squashfs makes the lean shape enough for the
# kitchen-sink dev install. boot_a / boot_b live where AML MPT puts
# them; system_a / system_b are the standard 516 MB slots.
SUPERBIRD_OTA_BOOT_A_OFFSET   = "0xd600000"
SUPERBIRD_OTA_BOOT_B_OFFSET   = "0xee00000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"

# Pull the artifacts from DEPLOY_DIR_IMAGE by their stable
# sw-description names. The superbird-flashthing bbclass drops
# `boot.img` + `system.img` symlinks in DEPLOY_DIR_IMAGE that point
# at the timestamped rootfs artifact under whatever extension
# SUPERBIRD_ROOTFS_TYPE selected (ext4, squashfs-zst, etc.) - the OTA
# pipeline is filesystem-agnostic.
SWUPDATE_IMAGES = "boot.img system.img"

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
        ${UNPACKDIR}/sw-description
}
addtask render_sw_description after do_unpack before do_swuimage

# Make sure the dev image's flashthing zip + boot.img are in
# DEPLOY_DIR_IMAGE before we try to pack them.
do_swuimage[depends] += "bridgething-dev-image:do_flashthing_zip"
