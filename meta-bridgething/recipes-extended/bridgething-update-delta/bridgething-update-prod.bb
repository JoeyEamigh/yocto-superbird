SUMMARY = "Bridgething OTA artifact (.swu) - delta against bridgething-prod-image"

require ${THISDIR}/bridgething-update-delta.inc

BRIDGETHING_OTA_SOURCE_IMAGE = "bridgething-prod-image"

# Match prod's rootfs type so do_stage_swu_inputs picks the right
# DEPLOY_DIR_IMAGE artifact (`...-${MACHINE}.ext4.zck.zckheader`).
SUPERBIRD_ROOTFS_TYPE = "ext4"
