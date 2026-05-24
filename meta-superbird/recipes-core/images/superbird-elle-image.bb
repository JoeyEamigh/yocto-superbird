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
