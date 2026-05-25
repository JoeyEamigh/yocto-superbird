# shared image base for prod + dev. byte-identical layout; per-variant recipes overlay install set.

inherit core-image
inherit superbird-flashthing

# locale + swiftshader trim for chromium
require bridgething-chromium-slim.inc

IMAGE_FEATURES += "ssh-server-openssh post-install-logging"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-bridgething-core \
"

# block transitive rrecommends that pull dead weight (full kernel-modules, wifi stack, hwdb)
BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static"

# specific .ko modules; left out of vmlinuz to keep boot.img under the 16 MiB cap
IMAGE_INSTALL += " \
    kernel-module-meson-vdec \
    kernel-module-ge2d \
"

# stock aml mpt geometry; 516 MB system_a / system_b
SUPERBIRD_PART_TABLE = "system_a:0x10600000:0x2040b000,system_b:0x3120b000:0x2040b000,settings:0x52e16000:0x10000000,data:0x63616000:0x859ea000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET = "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET = "0x3120b000"
SUPERBIRD_FLASH_VIA_AML_PARTITIONS = "yes"

# default squashfs-lz4; prod overrides to ext4
SUPERBIRD_ROOTFS_TYPE ??= "squashfs-lz4"
EXTRA_IMAGECMD:squashfs-lz4 = "-b 1M -no-xattrs -all-root -Xhc"
EXTRA_IMAGECMD:ext4 = "-i 16384 -m 0 -O ^has_journal -O ^huge_file"

# zck derivatives so bridgething-update-{dev,prod} can build delta swus
IMAGE_FSTYPES = "${SUPERBIRD_ROOTFS_TYPE} ${SUPERBIRD_ROOTFS_TYPE}.zck ${SUPERBIRD_ROOTFS_TYPE}.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"

# baked into /etc/superbird so the companion manifest poll routes to the right channel + artifacts
BRIDGETHING_CHANNEL ?= "stable"
BRIDGETHING_IMAGE_VARIANT ?= "prod"
BRIDGETHING_IMAGE_VERSION ?= "${DISTRO_VERSION}"

# build-identity stamps filled in at preprocess time so DATETIME doesn't churn package sstate
BRIDGETHING_IMAGE_BUILD_ID = "${DISTRO}-${DISTRO_VERSION}-${DATETIME}"
BRIDGETHING_IMAGE_BUILD_DATE = "${@d.getVar('DATETIME')[0:4]}-${@d.getVar('DATETIME')[4:6]}-${@d.getVar('DATETIME')[6:8]}T${@d.getVar('DATETIME')[8:10]}:${@d.getVar('DATETIME')[10:12]}:${@d.getVar('DATETIME')[12:14]}Z"
BRIDGETHING_IMAGE_BUILD_ID[vardepsexclude] = "DATETIME"
BRIDGETHING_IMAGE_BUILD_DATE[vardepsexclude] = "DATETIME"

bridgething_meta_postprocess() {
    sed -i \
        -e "s|@BRIDGETHING_CHANNEL@|${BRIDGETHING_CHANNEL}|g" \
        -e "s|@BRIDGETHING_IMAGE_VARIANT@|${BRIDGETHING_IMAGE_VARIANT}|g" \
        -e "s|@BRIDGETHING_IMAGE_VERSION@|${BRIDGETHING_IMAGE_VERSION}|g" \
        -e "s|@IMAGE_BUILD_ID@|${BRIDGETHING_IMAGE_BUILD_ID}|g" \
        -e "s|@IMAGE_BUILD_DATE@|${BRIDGETHING_IMAGE_BUILD_DATE}|g" \
        ${IMAGE_ROOTFS}${datadir}/superbird/meta.json.in
}
# bitbake's sed-arg parser misses ${VAR} refs; declare deps explicitly so the postprocess reruns
_BRIDGETHING_META_VARDEPS = " \
    BRIDGETHING_CHANNEL \
    BRIDGETHING_IMAGE_VARIANT \
    BRIDGETHING_IMAGE_VERSION \
    BRIDGETHING_IMAGE_BUILD_ID \
    BRIDGETHING_IMAGE_BUILD_DATE \
"
bridgething_meta_postprocess[vardeps] += "${_BRIDGETHING_META_VARDEPS}"
IMAGE_PREPROCESS_COMMAND[vardeps] += "${_BRIDGETHING_META_VARDEPS}"
do_image[vardeps] += "${_BRIDGETHING_META_VARDEPS}"
IMAGE_PREPROCESS_COMMAND:append = " bridgething_meta_postprocess; "
