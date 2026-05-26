# meta-bridgething

The application layer for the bridgething project. Composes on top of
meta-superbird: inherits `conf/distro/superbird.conf`, overrides the
brand knobs, and adds the daemon, the hub and stock webapps, the OTA
wrappers (image and bandaid), the bandaid ext4 generator, and the
prod/dev image recipes.

This layer is bridgething-specific. If you want a fork-template for
your own kiosk on the same BSP, copy
`examples/meta-superbird-kiosk-example/` instead.

## Build

```bash
just build bridgething        # default
just build bridgething-local  # use an unpushed bridgething checkout
```

The `bridgething-local` target needs a `kas/bridgething-local.yml` (copy
the `.example.yml` and edit `BRIDGETHING_LOCAL` to point at your
checkout).

Outputs:

- `bridgething-prod-image-superbird-flashthing.zip` (ext4 read-only rootfs, chromium kiosk with cast_shell, weston kiosk-shell)
- `bridgething-dev-image-superbird-flashthing.zip` (squashfs-lz4 rootfs, weston desktop + VNC, dev tools)
- `bridgething-update*-superbird.swu` (image OTA payloads, full and zck delta)
- `bandaid.ext4` (the daemon binary plus hub and stock for the bandaid partition; flashed initially, OTA'd as `applyUpdate { kind: daemon | builtin-webapp }`)

## Layer deps

`LAYERDEPENDS_bridgething = "core superbird"`. Pulls poky and
meta-superbird, which in turn brings meta-meson and
meta-browser/meta-chromium.

## Distro

`conf/distro/bridgething.conf` inherits `superbird.conf` and sets:

- `SUPERBIRD_HOSTNAME = "bridgething"`
- `SUPERBIRD_MDNS_SERVICE_NAME = "Bridgething"`
- `SUPERBIRD_USB_GADGET_*` to the bridgething identity strings
- `SUPERBIRD_BOOT_LOGO_NAME = "bridgething-bootup.bmp"`
- `CHROMIUM_KIOSK_PROXY_SERVER = "socks5://127.0.0.1:1080"` so chromium routes through the bluetooth RFCOMM tunnel
