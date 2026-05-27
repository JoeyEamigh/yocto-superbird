# meta-superbird-kiosk-example

A complete reference image stack on top of meta-superbird: distro, prod
and dev image variants, OTA wrappers, a minimal example daemon, a
placeholder webapp, and a bandaid generator. Copy this directory, run
search-and-replace on the vendor name, drop in your daemon and webapp,
and you have a working image.

## What you get out of the box

```bash
just build example-kiosk
```

builds, in `build/tmp/deploy/images/superbird/`:

| Artifact                                                | What it is                                                                 |
| ------------------------------------------------------- | -------------------------------------------------------------------------- |
| `superbird-kiosk-prod-image-superbird-flashthing.zip`   | ext4 ro rootfs, chromium kiosk, the example daemon, the placeholder webapp |
| `superbird-kiosk-dev-image-superbird-flashthing.zip`    | squashfs-lz4 rootfs, weston desktop + VNC + dev tools                      |
| `superbird-kiosk-update-{prod,dev}-superbird.swu`       | full image OTA payloads                                                    |
| `superbird-kiosk-update-{prod,dev}-delta-superbird.swu` | delta image OTA payloads (zchunk)                                          |
| `bandaid.ext4`                                          | daemon + webapp ext4 spliced into the flashthing zip at first flash        |

Flash either zip through `flashthing-cli`; both come up on
`superbird-kiosk.local` over USB-CDC-NCM. Dev image opens an SSH server
with an empty root password and a weston desktop shell with the VNC
backend, prod image runs the chromium kiosk against
`file:///usr/share/superbird-kiosk-default/index.html`.

## Layer shape

```
examples/meta-superbird-kiosk-example/
├── conf/
│   ├── layer.conf
│   └── distro/superbird-kiosk.conf            inherits superbird.conf, overrides brand knobs
├── recipes-core/
│   ├── images/
│   │   ├── superbird-kiosk-image-base.inc     shared image inc (rootfs type, OTA, headroom, flashthing)
│   │   ├── superbird-kiosk-prod-image.bb      ext4 RO + weston kiosk
│   │   └── superbird-kiosk-dev-image.bb       squashfs-lz4 + dev tools
│   ├── packagegroups/
│   │   ├── packagegroup-superbird-kiosk-core.bb   BSP runtime + chromium kiosk stack
│   │   └── packagegroup-superbird-kiosk-dev.bb    weston desktop + VNC + dev tools
│   └── superbird-kiosk-bandaid/
│       └── superbird-kiosk-bandaid.bb         two-line recipe over bandaid-image.bbclass
├── recipes-extended/
│   ├── superbird-kiosk-daemon/                minimal C heartbeat daemon, recipe, service
│   ├── superbird-kiosk-default-webapp/        single-page placeholder
│   └── superbird-kiosk-update/                full + delta OTA wrappers (prod + dev)
└── recipes-graphics/
    └── chromium-kiosk/chromium-kiosk_%.bbappend  point KIOSK_ENV_OVERRIDE_FILE at the vendor opt-overlay
```

## Knobs

Defaults live in `conf/distro/superbird-kiosk.conf`; everything inherited
from `conf/distro/superbird.conf` is in scope too. All BSP knobs use
`?=` so the example distro can override them.

Identity + brand (override per your product):

| Knob                                | Example value     | Effect                                                 |
| ----------------------------------- | ----------------- | ------------------------------------------------------ |
| `SUPERBIRD_HOSTNAME`                | `superbird-kiosk` | `/etc/hostname`, mDNS hostname prefix                  |
| `SUPERBIRD_MDNS_SERVICE_NAME`       | `Superbird Kiosk` | avahi service display field                            |
| `SUPERBIRD_USB_GADGET_NAME`         | `superbird-kiosk` | configfs gadget instance                               |
| `SUPERBIRD_USB_GADGET_MANUFACTURER` | `superbird-kiosk` | USB iManufacturer string                               |
| `SUPERBIRD_USB_GADGET_PRODUCT`      | `Superbird Kiosk` | USB iProduct string                                    |
| `SUPERBIRD_BOOT_LOGO_NAME`          | `bootup.bmp`      | source BMP packed into boot_a/b vfat                   |
| `SUPERBIRD_WESTON_SPLASH_IMAGE`     | `splash.png`      | source PNG copied to `/usr/share/superbird/splash.png` |

Chromium kiosk:

| Knob                          | Example value                                          | Effect                                        |
| ----------------------------- | ------------------------------------------------------ | --------------------------------------------- |
| `CHROMIUM_KIOSK_URL`          | `file:///usr/share/superbird-kiosk-default/index.html` | URL the kiosk loads at boot                   |
| `CHROMIUM_KIOSK_PROXY_SERVER` | `""`                                                   | `--proxy-server` flag value (empty = no flag) |

Partition geometry (inherited from `superbird.conf`; raise root or
bandaid if your daemon and webapps need more room):

| Knob                          | Default (MiB) |
| ----------------------------- | ------------- |
| `SUPERBIRD_ENV_PART_SIZE`     | 8             |
| `SUPERBIRD_BOOT_PART_SIZE`    | 64            |
| `SUPERBIRD_ROOT_PART_SIZE`    | 516           |
| `SUPERBIRD_BANDAID_PART_SIZE` | 192           |
| `SUPERBIRD_MIN_EMMC_SIZE_MIB` | 3600          |

Boot policy:

| Knob                           | Default | Effect                                                                 |
| ------------------------------ | ------- | ---------------------------------------------------------------------- |
| `SUPERBIRD_SLOT_INITIAL_TRIES` | `3`     | `slot_X_tries` initial value                                           |
| `SUPERBIRD_QUICK_BOOT`         | `"0"`   | `"1"` skips u-boot panel probe + splash (faster boot, no brand moment) |

Bandaid:

| Knob               | Required | Effect                                                                                                               |
| ------------------ | -------- | -------------------------------------------------------------------------------------------------------------------- |
| `BANDAID_VENDOR`   | yes      | top-level directory in `bandaid.ext4` and the opt-overlay instance name                                              |
| `BANDAID_PACKAGES` | yes      | ipks unpacked into the bandaid ext4; their `/usr/lib/${BANDAID_VENDOR}/` payload is rebased to `/${BANDAID_VENDOR}/` |

The bandaid recipe's full body is two lines:

```
BANDAID_VENDOR = "superbird-kiosk"
BANDAID_PACKAGES = "superbird-kiosk-daemon superbird-kiosk-default-webapp"

inherit bandaid-image
```

## Stand up your own repo

You don't fork yocto-superbird itself; you pull it in as a kas-managed
dependency and put your own layer in your own repo. Final shape:

```
yocto-yourproduct/                your repo root
├── kas/
│   └── yourproduct.yml           includes yocto-superbird's kas/superbird.yml
├── meta-yourproduct/             copied + renamed from this example
│   ├── conf/{layer.conf, distro/yourproduct.conf}
│   ├── recipes-core/...
│   └── recipes-extended/...
├── .gitignore                    sources/, build/, ccache/, .kas/
├── Justfile                      thin wrapper around kas-container (optional)
└── sources/                      kas-managed clones; gitignored
```

Minimum `kas/yourproduct.yml`:

```yaml
header:
  version: 18
  includes:
    - repo: yocto-superbird
      file: kas/superbird.yml

distro: yourproduct

target:
  - yourproduct-prod-image
  - yourproduct-dev-image
  - yourproduct-update-prod
  - yourproduct-update-prod-delta
  - yourproduct-update-dev
  - yourproduct-update-dev-delta

repos:
  yocto-superbird:
    url: https://github.com/JoeyEamigh/yocto-superbird.git
    branch: main
    commit: <sha-of-a-yocto-superbird-commit-you-have-built>
    path: sources/yocto-superbird

  yocto-yourproduct:
    path: .
    layers:
      meta-yourproduct:
```

Pin `commit:` to a specific yocto-superbird SHA, not a branch tip:
otherwise two builders fetching minutes apart get different sources and
zero shared sstate. Bump the pin when you want to ride forward.

`.gitignore`:

```
/build/
/sources/
/ccache/
/.kas/
```

Minimum `Justfile` (skip if you'd rather call `kas-container` directly):

```just
default := "yourproduct"

build target=default:
  kas-container build kas/{{target}}.yml

shell target=default:
  kas-container shell kas/{{target}}.yml

flash image="yourproduct-dev-image":
  flashthing-cli build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing.zip

clean-build:
  rm -rf build
```

Host prereqs: `docker` (or `podman`; export `KAS_CONTAINER_ENGINE=podman`),
`kas` (`pipx install kas`), `just`, and `flashthing-cli`. The kas
container carries bitbake + every cross toolchain.

## Fork checklist

Inside your new repo (with `meta-yourproduct/` copied in from
`examples/meta-superbird-kiosk-example/` of the BSP repo):

1. **Rename the layer.** In `conf/layer.conf`, replace the collection name and the matching `BBFILE_*` / `LAYERDEPENDS_*` / `LAYERSERIES_COMPAT_*` keys with `yourproduct`.
2. **Rename the distro.** `mv conf/distro/superbird-kiosk.conf conf/distro/yourproduct.conf`. Edit `DISTRO`, `DISTRO_NAME`, the `SUPERBIRD_*` brand knobs, and `CHROMIUM_KIOSK_URL`.
3. **Swap the daemon.** Replace `recipes-extended/superbird-kiosk-daemon/` with your own daemon recipe. Keep the install layout (`${nonarch_libdir}/yourproduct/daemon/<binary>.current` + an empty `/opt/yourproduct` dir, plus a `Requires=opt-overlay@yourproduct.service` service file). The recipe's `do_compile` is where your build commands go - `inherit cargo` for Rust, `inherit cmake` for C++, etc.
4. **Swap the webapp.** Replace `recipes-extended/superbird-kiosk-default-webapp/files/index.html` with your real bundle, or drop the recipe entirely if you serve from elsewhere.
5. **Rename the bandaid recipe + packagegroup + image recipes.** Search-and-replace `superbird-kiosk` -> `yourproduct` across `recipes-core/` and `recipes-extended/`. Update `BANDAID_PACKAGES` to your daemon + webapp recipe names.
6. **Build.** `just build` (or `kas-container build kas/yourproduct.yml`). Iterate on the dev image; cut prod when you're happy.

Nothing in meta-superbird should need changing for a kiosk fork; if
you find yourself reaching for it, the BSP isn't carrying its weight.

## Iteration loop

```bash
just build example-kiosk             # full build
just flash superbird-kiosk-dev-image  # first flash
just boot-kernel                     # cold-boot the new image
ping -c1 superbird-kiosk.local        # wait for mdns
just ssh                             # weston-desktop + ssh; iterate from here
```

Single-binary refresh while iterating, without a full reflash: push the
built binary into the bandaid bind-mount and atomic-rotate.

```bash
just build example-kiosk
KIOSKD=$(ls build/tmp/work/cortexa53-poky-linux/superbird-kiosk-daemon/*/build/kioskd | tail -n1)
scp "$KIOSKD" superbird-kiosk.local:/opt/superbird-kiosk/daemon/kioskd.incoming
ssh root@superbird-kiosk.local '
    mv /opt/superbird-kiosk/daemon/kioskd.current /opt/superbird-kiosk/daemon/kioskd.previous 2>/dev/null
    mv /opt/superbird-kiosk/daemon/kioskd.incoming /opt/superbird-kiosk/daemon/kioskd.current
    systemctl restart superbird-kiosk-daemon
'
```

`/opt/superbird-kiosk/` is the opt-overlay bind to bandaid, so the
write is durable across reboots and survives a slot flip. The example
daemon doesn't wire rollback; if you want failed-start fallback, ship
a service that rotates `.previous` back into `.current` on `OnFailure=`.

## OTA

The layer ships four wrappers around the BSP's image-OTA machinery:

- `superbird-kiosk-update-prod` (full)
- `superbird-kiosk-update-prod-delta` (zchunk delta)
- `superbird-kiosk-update-dev` (full)
- `superbird-kiosk-update-dev-delta` (zchunk delta)

Each emits a `.swu` that swupdate writes to the inactive A/B slot, then
flips `slot_active` + resets `slot_X_tries` and reboots. The delta
variants reuse chunks from the active slot via `CONFIG_DELTA_SOURCE_RAW`
and stream only the differences over HTTP.

Pushing a `.swu` from a host is one `swupdate-client` invocation against
the device's swupdate socket. The layer doesn't ship a higher-level
update driver; building one (push from a phone, range-proxy delta
chunks over a serial link, etc.) is up to you.

For daemon-only or webapp-only updates that skip the slot flip, the
bandaid bind-mount is the surface: `/var/lib/bandaid/<vendor>` is bound
to `/opt/<vendor>`, and a writer-of-your-choice can stream a new
binary in and atomic-rotate `.current`/`.previous`. The example daemon
ships this layout from factory (`<floor>/daemon/kioskd.current`) so the
slot is ready the moment you're ready to wire up the driver.
