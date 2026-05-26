# meta-superbird-kiosk-example

A reference chromium kiosk image built on top of meta-superbird. Use it
as a starting point for shipping your own kiosk on the Car Thing.

## What's in here

- `recipes-core/images/superbird-kiosk-example-image.bb`: requires the BSP image and adds `superbird-weston-init-kiosk`, `chromium-kiosk`, and a baked default webapp.
- `recipes-extended/superbird-kiosk-default-webapp/`: a placeholder webapp installed at `/usr/share/superbird-kiosk-default/index.html`.
- `recipes-graphics/chromium-kiosk/chromium-kiosk_%.bbappend`: points the launcher at `/etc/kiosk-overrides.env` so you can change `KIOSK_URL`, `KIOSK_PROXY_SERVER`, or `KIOSK_EXTRA` at runtime without rebuilding.

## Build

```bash
just build example-kiosk
```

The `kas/example-kiosk.yml` target adds this layer to the BSP set and
overrides `CHROMIUM_KIOSK_URL` to point at the baked default webapp.

## Fork it

Copy this directory into your own tree, then:

1. Rename the layer in `conf/layer.conf` (the collection name and the matching `BBFILE_*` keys).
2. Point the webapp recipe at your own bundle (or drop the webapp recipe and serve from elsewhere).
3. Override `CHROMIUM_KIOSK_URL` in your kas file (or your own distro conf) to point at the page the kiosk should load.

If you also want runtime overrides, keep the `chromium-kiosk_%.bbappend`
and write key=value lines to wherever you point `KIOSK_ENV_OVERRIDE_FILE`.
