# meta-superbird

The BSP layer for the Spotify Car Thing (Superbird, Amlogic S905D2).
Provides everything between the silicon and your application: kernel
(mainline v7.x plus the patch stack the panel + BT + rotary need), DTS,
mainline u-boot 2026.07 with a signed FIP, the `superbird` distro, GPT
partition geometry, USB-CDC-NCM gadget, BCM20703A2 bluetooth, ALS, mic,
and baseline systemd units for provisioning, slot management, and CPU
freq capping.

No application code. To ship something, layer your own recipes on top
or fork `examples/meta-superbird-kiosk-example/` for a chromium kiosk
starting point.

## Build the bare BSP

```bash
just build superbird
```

The output `superbird-bsp-image-superbird-flashthing.zip` carries
kernel, busybox, sshd, and the USB gadget. No graphics, no webapp.

## Distro knobs

Defaults live in `conf/distro/superbird.conf`. All use `?=` so a
downstream distro can override.

The knob groups:

- identity (`SUPERBIRD_HOSTNAME`, `SUPERBIRD_MDNS_SERVICE_NAME`, `SUPERBIRD_USB_GADGET_*`, `SUPERBIRD_BOOT_LOGO_NAME`, `SUPERBIRD_WESTON_SPLASH_IMAGE`)
- partition geometry (`SUPERBIRD_{ENV,BOOT,ROOT,BANDAID}_PART_SIZE`, `SUPERBIRD_MIN_EMMC_SIZE_MIB`, `SUPERBIRD_REQUIRE_OTA_HEADROOM`)
- chromium kiosk (`CHROMIUM_KIOSK_URL`, `CHROMIUM_KIOSK_PROXY_SERVER`)
- performance (`SUPERBIRD_CPU_*`, `SUPERBIRD_GPU_FREQ_HZ`)
- boot policy (`SUPERBIRD_SLOT_INITIAL_TRIES`, `SUPERBIRD_QUICK_BOOT`)

`bridgething.conf` in meta-bridgething is one example of how to inherit
and override.

## Layer deps

`LAYERDEPENDS_superbird = "core meson chromium-browser-layer"`. The OTA
recipes also need meta-swupdate at parse time via `inherit swupdate`,
so add it to your kas or bblayers if you build the BSP OTA wrapper.
