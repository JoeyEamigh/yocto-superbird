SUMMARY = "BSP A/B OTA .swu (zchunk delta)"

require recipes-core/superbird-bsp-update/superbird-bsp-update.inc

# zckheader only; full .zck fetched over http and reassembled against source slot before raw write.
SUPERBIRD_OTA_SYSTEM_ARTIFACT  = "${SUPERBIRD_OTA_SOURCE_LINKNAME}.squashfs.zck.zckheader"
SUPERBIRD_OTA_SYSTEM_CPIO_NAME = "system.img.zck.zckheader"
