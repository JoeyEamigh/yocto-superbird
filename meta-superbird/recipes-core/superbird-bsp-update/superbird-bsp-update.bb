SUMMARY = "Mainline BSP OTA artifact (.swu) for the wic A/B layout"
DESCRIPTION = "swupdate .swu that writes boot.vfat + the squashfs rootfs to the \
inactive A/B slot's GPT partitions (by partlabel) and flips the mainline u-boot \
slot_active / slot_X_tries env on success. Full-image (non-delta) dev-testbed path; \
the wic's real partitions make this a straight raw write per slot, no AML-MPT \
offsets. Build it, then drive with `superbird-ota <swu>` on the device."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

# No signing for v1 (matches the bridgething OTA recipes).
SWUPDATE_SIGNING ??= ""

inherit swupdate

SRC_URI = "file://sw-description"

# Stage cpio inputs under bare names in a PN-keyed subdir so swupdate-common
# packs them as boot.vfat / system.img without racing other deploy symlinks.
SWU_STAGEDIR = "${PN}-stage"
SWUPDATE_IMAGES = "${SWU_STAGEDIR}/boot.vfat ${SWU_STAGEDIR}/system.img"

# Render @@VERSION@@ so a daemon could refuse stale .swu replays.
SWU_VERSION = "${DISTRO_VERSION}+${DATETIME}"
SWU_VERSION[vardepsexclude] = "DATETIME"

do_stage_swu_inputs() {
    install -d ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}
    install -m 0644 ${DEPLOY_DIR_IMAGE}/boot.vfat \
        ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}/boot.vfat
    install -m 0644 ${DEPLOY_DIR_IMAGE}/superbird-elle-image-${MACHINE}.squashfs \
        ${DEPLOY_DIR_IMAGE}/${SWU_STAGEDIR}/system.img
}
do_stage_swu_inputs[depends] += " \
    superbird-boot-vfat:do_deploy \
    superbird-elle-image:do_image_complete \
"
addtask stage_swu_inputs after do_unpack before do_swuimage

# do_swuimage is a python task, so fill the placeholder in a separate shell task.
do_render_sw_description() {
    sed -i -e "s|@@VERSION@@|${SWU_VERSION}|g" ${UNPACKDIR}/sw-description
}
addtask render_sw_description after do_unpack before do_swuimage
