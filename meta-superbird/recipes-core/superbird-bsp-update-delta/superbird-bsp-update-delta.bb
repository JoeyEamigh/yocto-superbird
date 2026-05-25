SUMMARY = "Mainline BSP delta (zchunk) OTA artifact (.swu) for the wic A/B layout"
DESCRIPTION = "swupdate .swu that ships boot.vfat raw + the rootfs as a zchunk delta \
header. The delta handler reads chunks from the active slot's root partition, fetches \
the missing ones over HTTP from SUPERBIRD_DELTA_URL, reassembles, and writes the \
inactive slot by partlabel; bootenv flips slot_active. Pairs with superbird-elle-image \
(which emits squashfs.zck + .zckheader)."

require recipes-core/superbird-bsp-update/superbird-bsp-update.inc

# Zchunk header only - the .swu carries ~the chunk index; the full .zck is fetched
# over HTTP and reassembled against the source slot before the raw write to root_X.
SUPERBIRD_OTA_SYSTEM_ARTIFACT  = "${SUPERBIRD_OTA_SOURCE_LINKNAME}.squashfs.zck.zckheader"
SUPERBIRD_OTA_SYSTEM_CPIO_NAME = "system.img.zck.zckheader"
