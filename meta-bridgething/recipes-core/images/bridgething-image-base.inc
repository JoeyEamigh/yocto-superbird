inherit core-image
inherit superbird-headroom-check
inherit mainline-flashthing

MAINLINE_FLASHTHING_WITH_BANDAID = "1"

require bridgething-chromium-slim.inc

IMAGE_FEATURES += "ssh-server-openssh post-install-logging"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-bridgething-core \
"

BAD_RECOMMENDATIONS += "kernel-modules udev-hwdb wpa-supplicant wireless-regdb wireless-regdb-static weston-init"

SUPERBIRD_ROOTFS_TYPE ??= "ext4"
EXTRA_IMAGECMD:squashfs-lz4 = "-b 1M -no-xattrs -all-root -Xhc"
EXTRA_IMAGECMD:ext4 = "-i 16384 -m 0 -O ^has_journal -O ^huge_file"

IMAGE_FSTYPES = "wic ${SUPERBIRD_ROOTFS_TYPE} ${SUPERBIRD_ROOTFS_TYPE}.zck ${SUPERBIRD_ROOTFS_TYPE}.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"

WKS_FILE = "superbird-mainline.wks.in"

do_image_wic[depends] += " \
    virtual/kernel:do_deploy \
    superbird-uenv:do_deploy \
    superbird-extlinux:do_deploy \
    superbird-logo:do_deploy \
"

EXTRA_IMAGEDEPENDS += "superbird-uboot bridgething-bandaid"

BRIDGETHING_CHANNEL ?= "stable"
BRIDGETHING_IMAGE_VARIANT ?= "prod"
BRIDGETHING_IMAGE_VERSION ?= "${DISTRO_VERSION}"

# stamps frozen at preprocess so DATETIME doesn't churn package sstate.
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
# bitbake's sed-arg parser misses ${VAR} refs; declare deps so postprocess reruns.
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
