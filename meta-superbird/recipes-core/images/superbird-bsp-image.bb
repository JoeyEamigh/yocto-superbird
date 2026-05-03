SUMMARY = "Superbird BSP-only image - kernel + busybox + SSH"
DESCRIPTION = "Smallest flashable image for the Spotify Car Thing. \
Mainline v6.19 kernel with the BSP patches (panel, BT, touchscreen, \
rotary, ALS, pinctrl), busybox userspace, openssh, and the \
USB-CDC-ECM gadget so the device is reachable at 10.42.1.2 over the \
USB-C cable. No bridgething daemon, no chromium, no Cog. Useful as \
a BSP-bringup target, a kernel-iteration target, or a base for any \
non-bridgething userspace flashed onto Superbird hardware. \
\
Same partition geometry, same flash mechanic, and same OTA shape as \
the bridgething images - just with the application userspace stripped \
out."
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
