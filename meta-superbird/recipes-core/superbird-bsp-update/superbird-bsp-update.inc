# Shared machinery for the mainline-wic A/B OTA .swu recipes:
#   superbird-bsp-update.bb        - full-image (raw rootfs)
#   superbird-bsp-update-delta.bb  - zchunk delta (header + HTTP chunk fetch)
#
# Mirrors the swupdate idioms of meta-bridgething's bridgething-update-delta.inc
# (SWU_STAGEDIR, render-via-sed task, SWU_VERSION) but is a SEPARATE base, not an
# override of joey's: that .inc is bound to the stock AML-MPT layout (raw mmcblk0
# byte offsets, flashthing-zip symlinks, active_slot=_a vocab) and hard-depends on
# do_flashthing_zip, which the mainline wic image does not provide. Here every
# target is a by-partlabel GPT partition, inputs are install'd straight from
# DEPLOY_DIR_IMAGE, and the bootenv uses ab_boot's slot_active/slot_X_tries.
#
# Wrappers differ only in the rootfs artifact they stage (plain squashfs vs the
# zchunk header) and the sw-description they ship.

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

# No signing for v1 (matches the bridgething OTA recipes).
SWUPDATE_SIGNING ??= ""

inherit swupdate

SRC_URI = "file://sw-description"

# Image the .swu is built from; both boot.vfat and the rootfs come from its deploy.
# Indirection (rather than a hardcode) eases the later fold into the prod image.
SUPERBIRD_OTA_SOURCE_IMAGE ??= "superbird-elle-image"
SUPERBIRD_OTA_SOURCE_LINKNAME = "${SUPERBIRD_OTA_SOURCE_IMAGE}-${MACHINE}"

# Wrappers pin the rootfs artifact + its cpio entry name:
#   full  -> ${LINKNAME}.squashfs                  packed as system.img
#   delta -> ${LINKNAME}.squashfs.zck.zckheader    packed as system.img.zck.zckheader
SUPERBIRD_OTA_SYSTEM_ARTIFACT ??= ""
SUPERBIRD_OTA_SYSTEM_CPIO_NAME ??= ""

python __anonymous() {
    for v in ("SUPERBIRD_OTA_SYSTEM_ARTIFACT", "SUPERBIRD_OTA_SYSTEM_CPIO_NAME"):
        if not d.getVar(v):
            bb.fatal("%s must be set by the wrapping recipe" % v)
}

# Stage cpio inputs under bare names in a PN-keyed subdir so swupdate-common packs
# them as boot.vfat / <cpio name> without racing other deploy symlinks.
SWU_STAGEDIR = "${PN}-stage"
SWUPDATE_IMAGES = "${SWU_STAGEDIR}/boot.vfat ${SWU_STAGEDIR}/${SUPERBIRD_OTA_SYSTEM_CPIO_NAME}"

# Where the device fetches the full .zck (delta only). Override per test; default is
# the CDC-ECM host IP on the gadget subnet.
SUPERBIRD_DELTA_URL ?= "http://10.42.0.6:8893/system.img.zck"

# Render @@VERSION@@ so a daemon could refuse stale .swu replays. @@DELTA_URL@@ only
# appears in the delta sw-description; the sed is a harmless no-op for the full image.
SWU_VERSION = "${DISTRO_VERSION}+${DATETIME}"
SWU_VERSION[vardepsexclude] = "DATETIME"

do_stage_swu_inputs() {
    install -d ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}
    install -m 0644 ${DEPLOY_DIR_IMAGE}/boot.vfat \
        ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}/boot.vfat
    install -m 0644 ${DEPLOY_DIR_IMAGE}/${SUPERBIRD_OTA_SYSTEM_ARTIFACT} \
        ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}/${SUPERBIRD_OTA_SYSTEM_CPIO_NAME}
}
do_stage_swu_inputs[depends] += " \
    superbird-boot-vfat:do_deploy \
    ${SUPERBIRD_OTA_SOURCE_IMAGE}:do_image_complete \
"
addtask stage_swu_inputs after do_unpack before do_swuimage

# do_swuimage is a python task, so fill placeholders in a separate shell task.
do_render_sw_description() {
    sed -i \
        -e "s|@@VERSION@@|${SWU_VERSION}|g" \
        -e "s|@@DELTA_URL@@|${SUPERBIRD_DELTA_URL}|g" \
        ${UNPACKDIR}/sw-description
}
addtask render_sw_description after do_unpack before do_swuimage
