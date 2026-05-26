# shared machinery for the wic A/B .swu wrappers (full + delta).

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

SWUPDATE_SIGNING ??= ""

inherit swupdate

# so cross-layer wrappers find sw-description in this dir.
FILESEXTRAPATHS:prepend := "${@os.path.dirname(bb.utils.which(d.getVar('BBPATH'), 'recipes-core/superbird-bsp-update/superbird-bsp-update.inc'))}/files:"

SRC_URI = "file://sw-description"

SUPERBIRD_OTA_SOURCE_IMAGE ??= "superbird-elle-image"
SUPERBIRD_OTA_SOURCE_LINKNAME = "${SUPERBIRD_OTA_SOURCE_IMAGE}-${MACHINE}"

SUPERBIRD_OTA_SYSTEM_ARTIFACT ??= ""
SUPERBIRD_OTA_SYSTEM_CPIO_NAME ??= ""

python __anonymous() {
    for v in ("SUPERBIRD_OTA_SYSTEM_ARTIFACT", "SUPERBIRD_OTA_SYSTEM_CPIO_NAME"):
        if not d.getVar(v):
            bb.fatal("%s must be set by the wrapping recipe" % v)
}

SWU_STAGEDIR = "${PN}-stage"
SWUPDATE_IMAGES = "${SWU_STAGEDIR}/boot.vfat ${SWU_STAGEDIR}/${SUPERBIRD_OTA_SYSTEM_CPIO_NAME}"

SUPERBIRD_OTA_DELTA_URL_BASE ?= "http://127.0.0.1:8893"

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

# do_swuimage is a python task; render placeholders in a separate shell task.
do_render_sw_description() {
    sed -i \
        -e "s|@@VERSION@@|${SWU_VERSION}|g" \
        -e "s|@@DELTA_URL_BASE@@|${SUPERBIRD_OTA_DELTA_URL_BASE}|g" \
        ${UNPACKDIR}/sw-description
}
addtask render_sw_description after do_unpack before do_swuimage
