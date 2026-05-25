SUMMARY = "Superbird BSP-only image"
DESCRIPTION = "Mainline kernel + busybox + openssh + USB-CDC-NCM gadget. No avahi, no bridgething daemon, no chromium. Useful as a BSP / kernel iteration base."
LICENSE = "MIT"

inherit core-image
inherit superbird-flashthing

IMAGE_FEATURES += " \
    ssh-server-openssh \
    allow-empty-password \
    allow-root-login \
    empty-root-password \
    post-install-logging \
    serial-autologin-root \
"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    superbird-base-files \
    superbird-firmware \
    superbird-bluetooth \
    bridgething-usb-gadget \
    bluez5 \
    e2fsprogs \
    e2fsprogs-mke2fs \
    e2fsprogs-e2fsck \
    e2fsprogs-tune2fs \
"

BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static"

SUPERBIRD_PART_TABLE = "system_a:0x10600000:0x2040b000,system_b:0x3120b000:0x2040b000,settings:0x52e16000:0x10000000,data:0x63616000:0x859ea000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"
SUPERBIRD_FLASH_VIA_AML_PARTITIONS = "yes"

SUPERBIRD_ROOTFS_TYPE = "squashfs-lz4"
EXTRA_IMAGECMD:squashfs-lz4 = "-b 1M -no-xattrs -all-root -Xhc"

IMAGE_FSTYPES = "squashfs-lz4"
