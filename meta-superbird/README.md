# meta-superbird

The BSP layer for the Spotify Car Thing (Superbird, Amlogic S905D2).
Everything between the silicon and your application: kernel
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

Output `superbird-bsp-image-superbird-flashthing.zip` carries kernel,
busybox, sshd, the USB gadget, and the BSP runtime packagegroup. No
graphics, no webapp.

## Layer surface for downstreams

You inherit these from meta-superbird:

- **`conf/distro/superbird.conf`** - baseline distro with knob defaults. Inherit + override in your own distro conf.
- **`packagegroup-superbird-runtime`** - everything any superbird image needs at runtime (BSP firmware, peripherals, opt-overlay, swupdate, base utilities). RDEPEND on this from your application packagegroup; do not enumerate by hand.
- **`classes/superbird-image.bbclass`** - inherit alongside `core-image` to get the per-image substitutions into `/usr/share/superbird/meta.json.in` (image build id + date). Extend the meta.json shape by adding your own `IMAGE_PREPROCESS_COMMAND` that runs after `superbird_meta_postprocess`.
- **`classes/bandaid-image.bbclass`** - inherit in a two-line recipe to produce `bandaid.ext4` from a list of ipks. Set `BANDAID_VENDOR` + `BANDAID_PACKAGES`; the class handles the size, format, and `/usr/lib/<vendor>/` -> `/<vendor>/` rebase.
- **`classes/mainline-flashthing.bbclass`** - inherit in an image recipe to emit the flashthing zip (`superbird-boot.bin` + wic + optional `bandaid.ext4` + meta.json) for first-flash via flashthing-cli.
- **`recipes-core/superbird-bsp-update/superbird-bsp-update.inc`** - shared machinery for image OTA wrappers (full + delta). Set `SUPERBIRD_OTA_SOURCE_IMAGE` + the artifact + cpio names in your wrapper recipe.
- **`opt-overlay@<vendor>.service`** - templated bind-mount unit. `Requires=opt-overlay@<vendor>.service` from your application's main unit.
- **`superbird-init`** - renders `/var/lib/superbird/meta.json` from the build-time template, patches in efused btMac + serial, seeds the bluez alias. Pulled by `packagegroup-superbird-runtime`. Extend the meta.json shape from your image's `IMAGE_PREPROCESS_COMMAND` (run after `superbird_meta_postprocess`; awk-inject new fields before the closing brace).

## Distro knobs

Defaults in `conf/distro/superbird.conf`. All use `?=` so a downstream
distro can override.

Groups:

- identity (`SUPERBIRD_HOSTNAME`, `SUPERBIRD_MDNS_SERVICE_NAME`, `SUPERBIRD_USB_GADGET_*`, `SUPERBIRD_BOOT_LOGO_NAME`, `SUPERBIRD_WESTON_SPLASH_IMAGE`)
- partition geometry (`SUPERBIRD_{ENV,BOOT,ROOT,BANDAID}_PART_SIZE`, `SUPERBIRD_MIN_EMMC_SIZE_MIB`, `SUPERBIRD_REQUIRE_OTA_HEADROOM`)
- chromium kiosk (`CHROMIUM_KIOSK_URL`, `CHROMIUM_KIOSK_PROXY_SERVER`)
- boot policy (`SUPERBIRD_SLOT_INITIAL_TRIES`, `SUPERBIRD_QUICK_BOOT`)
- OTA (`SUPERBIRD_OTA_RANGE_PORT`, `SUPERBIRD_OTA_DELTA_URL_BASE`)

## Layer deps

`LAYERDEPENDS_superbird = "core meson chromium-browser-layer"`. OTA
recipes pull `inherit swupdate` at parse time, so add meta-swupdate to
your kas or bblayers if you build the BSP OTA wrappers.
