# meta-superbird-kiosk-example

A reference kiosk image built on top of `meta-superbird`. Demonstrates the
minimum a downstream needs to wire to ship their own chromium-kiosk on a
Car Thing: a static webapp recipe and a `CHROMIUM_KIOSK_URL` override.

## What it contains

- `recipes-core/images/superbird-kiosk-example-image.bb` — requires the
  BSP image, adds `superbird-weston-init-kiosk`, `chromium-kiosk`, and a
  baked default webapp.
- `recipes-extended/superbird-kiosk-default-webapp/` — placeholder
  webapp shipped at `/usr/share/superbird-kiosk-default/index.html`.

## Build

```sh
just build example-kiosk
```

That kas target adds this layer to the BSP set and overrides
`CHROMIUM_KIOSK_URL` to point at the baked default webapp.

## Fork it

Copy this directory into your own tree, rename the layer
(`conf/layer.conf` collection + `BBFILE_*` keys), point the webapp
recipe at your bundle, and replace the `CHROMIUM_KIOSK_URL` override in
your kas file.
