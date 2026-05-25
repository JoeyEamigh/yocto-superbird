SUMMARY = "BSP A/B OTA .swu (full rootfs)"

require ${THISDIR}/superbird-bsp-update.inc

SUPERBIRD_OTA_SYSTEM_ARTIFACT  = "${SUPERBIRD_OTA_SOURCE_LINKNAME}.squashfs"
SUPERBIRD_OTA_SYSTEM_CPIO_NAME = "system.img"
