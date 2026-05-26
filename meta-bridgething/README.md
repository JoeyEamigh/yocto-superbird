# meta-bridgething

Application layer for Joey's bridgething Rust daemon. Composes on top
of meta-superbird: pulls `conf/distro/superbird.conf` and overrides
brand knobs to `bridgething` defaults; adds the daemon recipe, the
hub and stock webapps, OTA wrappers (image + bandaid), the bandaid
ext4 generator, and the prod/dev image recipes.

## Build

```sh
just build              # default: bridgething (kas/bridgething.yml)
just build bridgething-local  # local-source override
```

Produces:

- `bridgething-prod-image-superbird-flashthing.zip` (ext4 ro rootfs,
  chromium kiosk + cast_shell, weston kiosk-shell)
- `bridgething-dev-image-superbird-flashthing.zip` (squashfs-lz4
  rootfs, weston desktop + VNC, dev tools)
- `bridgething-update*-superbird.swu` (image OTA payloads, full + zck
  delta)
- `bandaid.ext4` (separate artifact carrying the daemon binary + hub +
  stock for the bandaid partition; written at first flash, OTA'd via
  the daemon's `applyUpdate { kind: daemon | builtin-webapp }`)

## Layer deps

`LAYERDEPENDS_bridgething = "core superbird webkit
chromium-browser-layer"`. Pulls poky + meta-superbird + meta-webkit
+ meta-browser/meta-chromium.

## Distro

`conf/distro/bridgething.conf` inherits `superbird.conf` and sets:

- `SUPERBIRD_HOSTNAME = "bridgething"`
- `SUPERBIRD_MDNS_SERVICE_NAME = "Bridgething"`
- `SUPERBIRD_USB_GADGET_*` to the bridgething identity strings
- `SUPERBIRD_BOOT_LOGO_NAME = "bridgething-bootup.bmp"`
- `CHROMIUM_KIOSK_PROXY_SERVER = "socks5://127.0.0.1:1080"` for the
  bluetooth RFCOMM tunnel
