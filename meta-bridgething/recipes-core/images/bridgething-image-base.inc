inherit core-image
inherit superbird-headroom-check
inherit superbird-image
inherit mainline-flashthing

MAINLINE_FLASHTHING_WITH_BANDAID = "1"
MAINLINE_FLASHTHING_BANDAID_RECIPE = "bridgething-bandaid"

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

# inject channel + imageVariant + imageVersion before the closing brace; the
# BSP's superbird_meta_postprocess (run earlier in IMAGE_PREPROCESS_COMMAND)
# already substituted @IMAGE_BUILD_ID@ + @IMAGE_BUILD_DATE@.
bridgething_meta_postprocess() {
    META=${IMAGE_ROOTFS}${datadir}/superbird/meta.json.in
    if [ ! -f "$META" ]; then
        return
    fi
    awk -v c="${BRIDGETHING_CHANNEL}" -v v="${BRIDGETHING_IMAGE_VARIANT}" -v ver="${BRIDGETHING_IMAGE_VERSION}" '
        /^}$/ {
            print "  \"channel\": \"" c "\","
            print "  \"imageVariant\": \"" v "\","
            print "  \"imageVersion\": \"" ver "\""
            print
            next
        }
        /^  "imageBuildDate":/ {
            sub(/$/, ",")
            print
            next
        }
        { print }
    ' "$META" > "$META.tmp" && mv "$META.tmp" "$META"
}

_BRIDGETHING_META_VARDEPS = " \
    BRIDGETHING_CHANNEL \
    BRIDGETHING_IMAGE_VARIANT \
    BRIDGETHING_IMAGE_VERSION \
"
bridgething_meta_postprocess[vardeps] += "${_BRIDGETHING_META_VARDEPS}"
IMAGE_PREPROCESS_COMMAND[vardeps] += "${_BRIDGETHING_META_VARDEPS}"
do_image[vardeps] += "${_BRIDGETHING_META_VARDEPS}"
IMAGE_PREPROCESS_COMMAND:append = " bridgething_meta_postprocess; "
