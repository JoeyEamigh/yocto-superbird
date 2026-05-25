SUMMARY = "Mainline BSP OTA artifact (.swu) for the wic A/B layout"
DESCRIPTION = "swupdate .swu that writes boot.vfat + the squashfs rootfs to the \
inactive A/B slot's GPT partitions (by partlabel) and flips the mainline u-boot \
slot_active / slot_X_tries env on success. Full-image (non-delta) dev-testbed path; \
the wic's real partitions make this a straight raw write per slot, no AML-MPT \
offsets. Build it, then drive with `superbird-ota <swu>` on the device."

require ${THISDIR}/superbird-bsp-update.inc

# Plain squashfs, written raw to root_X.
SUPERBIRD_OTA_SYSTEM_ARTIFACT  = "${SUPERBIRD_OTA_SOURCE_LINKNAME}.squashfs"
SUPERBIRD_OTA_SYSTEM_CPIO_NAME = "system.img"
