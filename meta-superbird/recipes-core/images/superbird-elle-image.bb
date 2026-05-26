require superbird-bsp-image.bb

SUMMARY = "BSP image + weston + alsa-utils + A/B OTA testbed"

IMAGE_INSTALL:append = " \
    superbird-weston-init-desktop \
    cursor-suppress \
    weston-vnc-backend \
    weston-examples \
    alsa-utils \
    swupdate \
    swupdate-config \
    superbird-ota \
"

# emit squashfs.zck + .zckheader for the delta-OTA path (full .zck fetched over http).
IMAGE_FSTYPES += "squashfs.zck squashfs.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"

# marker so this rootfs differs from the BSP image on the other slot; exercises the download path.
ota_test_marker() {
    echo "ota-delta-test" > ${IMAGE_ROOTFS}/etc/ota-test-marker
}
ROOTFS_POSTPROCESS_COMMAND += "ota_test_marker;"
