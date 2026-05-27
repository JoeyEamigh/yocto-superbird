SUMMARY = "Superbird BSP runtime"
DESCRIPTION = "Everything any image on this BSP needs at runtime: kernel-adjacent firmware, peripherals (BT, USB-gadget, ALS), provisioning + slot-ok, opt-overlay, swupdate, base utilities. Graphics + browser stay out so headless images stay lean."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    superbird-base-files \
    superbird-firmware \
    superbird-bluetooth \
    superbird-init \
    superbird-clock \
    superbird-timezone \
    superbird-usb-gadget \
    superbird-mdns \
    superbird-als \
    superbird-provision \
    superbird-slot-ok \
    superbird-cpufreq-cap \
    opt-overlay \
    swupdate \
    swupdate-config \
    libubootenv-bin \
    bluez5 \
    alsa-utils \
    zram \
    tzdata \
    e2fsprogs-mke2fs \
    e2fsprogs-e2fsck \
"
