# meta-superbird

BSP layer for the Spotify Car Thing (Superbird, Amlogic S905D2). Kernel
(mainline v7.x + patches), DTS, mainline u-boot 2026.07 + signed FIP,
boot flow (extlinux + GPT + slot_active), distro `superbird`, partition
geometry knobs, baseline systemd units (provision, slot-ok,
cpufreq-cap), USB-CDC-NCM gadget, BCM20703A2 bluetooth, ALS, mic.

No application code. A downstream layer (meta-bridgething, the example
in `examples/meta-superbird-kiosk-example`, or your own) consumes this
to build a shippable image.

## Build the BSP image

```sh
just build superbird
```

Produces `superbird-bsp-image-superbird-flashthing.zip` under
`build/tmp/deploy/images/superbird/`. The image carries kernel +
busybox + sshd + USB gadget + provisioning; no graphics, no webapp.

## Knobs

Defaults in `conf/distro/superbird.conf` (all `?=`, downstreams
override). Brand (`SUPERBIRD_HOSTNAME`, `SUPERBIRD_MDNS_SERVICE_NAME`,
`SUPERBIRD_USB_GADGET_*`, `SUPERBIRD_BOOT_LOGO_NAME`,
`SUPERBIRD_WESTON_SPLASH_IMAGE`), partition geometry
(`SUPERBIRD_{ENV,BOOT,ROOT,BANDAID}_PART_SIZE`,
`SUPERBIRD_MIN_EMMC_SIZE_MIB`, `SUPERBIRD_REQUIRE_OTA_HEADROOM`),
chromium kiosk (`CHROMIUM_KIOSK_URL`, `CHROMIUM_KIOSK_PROXY_SERVER`),
perf (`SUPERBIRD_CPU_*`, `SUPERBIRD_GPU_FREQ_HZ`), boot policy
(`SUPERBIRD_SLOT_INITIAL_TRIES`, `SUPERBIRD_QUICK_BOOT`).

## Layer deps

`LAYERDEPENDS_superbird = "core meson"`. The OTA recipes also pull
meta-swupdate at parse time via `inherit swupdate`; bring it in via
your kas / bblayers if you build the BSP OTA wrapper.
