require superbird-bsp-image.bb

SUMMARY = "Superbird elle image - BSP + display, audio tooling, A/B OTA testbed"
DESCRIPTION = "superbird-bsp-image plus the userspace extras our bring-up/iteration \
work needs but the lean BSP intentionally omits: a boot-Weston compositor on the \
panel, alsa-utils for the PDM mic array, and the swupdate A/B OTA stack. Same wic GPT \
/ mainline-u-boot boot path as the BSP it builds on. Scratch iteration image - expect \
it to be discarded once this work folds into the real images."

# weston-init-desktop is joey's known-good boot-Weston (DSI-1 480x800, rotated);
# cursor-suppress is load-bearing (weston.ini [core] modules= loads it, weston aborts
# without it, and weston-init doesn't RDEPEND it); vnc-backend + examples for the dev
# env. alsa-utils: arecord/amixer for the PDM mic array. swupdate stack: the A/B OTA
# testbed - superbird-ota applies a .swu to the inactive slot (pulls swupdate-client),
# swupdate-config carries swupdate.cfg + the slot select allowlist.
IMAGE_INSTALL:append = " \
    bridgething-weston-init-desktop \
    bridgething-cursor-suppress \
    weston-vnc-backend \
    weston-examples \
    alsa-utils \
    swupdate \
    swupdate-config \
    superbird-ota \
"

# --- zchunk delta OTA testbed ---
# Emit squashfs.zck + .zckheader alongside the plain squashfs. image_types_zchunk
# adds the zck/zckheader CONVERSIONTYPES; Yocto walks squashfs -> .zck -> .zck.zckheader.
# The delta .swu ships only the tiny .zckheader; the full .zck is fetched over HTTP.
IMAGE_FSTYPES += "squashfs.zck squashfs.zck.zckheader"
IMAGE_CLASSES += "image_types_zchunk"

# A marker file the lean device images don't carry, so this rootfs differs from
# whatever's already on the inactive slot - forces the delta handler to fetch the
# changed chunk(s) over HTTP (exercises the download path, not just source reuse).
ota_test_marker() {
    echo "ota-delta-test" > ${IMAGE_ROOTFS}/etc/ota-test-marker
}
ROOTFS_POSTPROCESS_COMMAND += "ota_test_marker;"
