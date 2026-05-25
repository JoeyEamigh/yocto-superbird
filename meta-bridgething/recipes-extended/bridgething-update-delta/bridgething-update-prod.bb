SUMMARY = "Bridgething prod-image delta OTA"

require ${THISDIR}/bridgething-update-delta.inc

BRIDGETHING_OTA_SOURCE_IMAGE = "bridgething-prod-image"

# match prod's rootfs type so the zck header artifact resolves
SUPERBIRD_ROOTFS_TYPE = "ext4"
