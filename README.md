# yocto-superbird

Mainline-Linux image stack for the Spotify Car Thing (Superbird, Amlogic
S905D2). Ships a kernel (mainline v7.x + a small patch stack for the
display panel, BT, touchscreen, and rotary encoder), mainline u-boot
2026.07 with a signed FIP, and either the bridgething daemon + chromium
kiosk or a bare BSP you can build on top of.

## Image variants

| Image                           | What it is                                                                               | Use                                                 |
| ------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `bridgething-prod-image`        | ext4 ro rootfs, chromium kiosk + cast_shell, weston kiosk-shell, bridgething daemon, OTA | what you flash on a car-thing you actually use      |
| `bridgething-dev-image`         | squashfs-lz4 rootfs, weston desktop + VNC, dev tools, debug auth                         | iteration; bigger, slower, has the dev knife drawer |
| `superbird-bsp-image`           | kernel + busybox + openssh + USB-CDC gadget, no graphics                                 | fork-template for non-bridgething userspace         |
| `superbird-kiosk-example-image` | BSP + weston + chromium kiosk + a placeholder webapp                                     | fork-template for a non-bridgething kiosk           |

All four ride the same GPT layout (env + boot_a + root_a + boot_b +
root_b + bandaid + data) and the same OTA pipeline.

## Use the prebuilt image (no Yocto)

For folks working on bridgething itself or just running the device: a
rolling `latest` GitHub release carries the most recent dev + prod
flashthing zips. Pull from that and skip the multi-hour Yocto build.

One-time:

```bash
gh auth login                  # gh CLI, repo scope (release is private)
cargo install flashthing-cli
sudo flashthing-cli --setup    # Linux udev rule for libusb burn-mode access
```

Then with the Car Thing in burn mode (hold the wheel-click button while
plugging in USB):

```bash
just install-dev               # or just install-prod
just boot-kernel               # exit burn mode into the new image
```

The device comes up ~18s after `boot-kernel` at `bridgething.local`
over USB-CDC-NCM (host needs a NetworkManager profile matched by MAC
prefix `02:11:44:*` to reach the device's DHCP server). Multi-device
hosts use `bridgething-<short-serial>.local` (per-device, auto-published
by avahi).

## Iterate on a connected device

```bash
just ssh                                  # interactive shell
just ssh 'uname -a'                       # one-shot
just ssh 'systemctl status bridgething'   # service status
```

Daemon hot-reload (atomic rotate on the bandaid bind-mount; survives
reboot, clobbered by reflash):

```bash
# from the bridgething repo:
just push                                  # cross-build + push the new daemon
# from this repo:
just push-webapp ./dist <webapp-name>      # bundle into /var/bridgething/webapps/
```

Chromium DevTools over the USB link (chromium >= M111 ignores
`--remote-debugging-address=<not-loopback>`, so we tunnel):

```bash
just cdp                                   # then http://localhost:9223/json/version
```

UART for pre-SSH or kernel-panic diagnostics:

```bash
just console start                         # long-lived agent on /dev/ttyUSB0
just cmd 'dmesg | tail -30'
just console stop
```

The console agent holds FT232 RTS deasserted (RTS is wired to the SoC
reset pin), so the board doesn't reset every time another process opens
the serial node. Don't open `/dev/ttyUSB0` directly while the agent is
running; it owns the port via a FIFO.

```bash
just reset-pulse 200                       # one-shot soft reset (200ms RTS LOW)
just boot-kernel                           # exit mask-rom + cold-boot the on-disk image
just reboot-to-maskrom                     # drop into 1b8e:c003 for a full wic flash
just reboot-to-fastboot                    # drop into u-boot fastboot for env / partition writes
```

## OTA

The bridgething daemon owns the apply path end-to-end. The companion app
pushes a `.swu` over the BT gateway; the daemon writes it via libswupdate
to the inactive slot, flips `slot_active`, and reboots. Three kinds:

- `image`: `.swu` to root_X via libswupdate, slot flip, reboot
- `daemon`: aarch64 binary atomic-rotated on the bandaid bind-mount, service restart
- `builtin-webapp`: hub/stock zip swapped on the bandaid bind-mount, service restart

The on-device `bridgething-ab` binary is a debug helper
(`status`, `set-slot a|b`); it does not drive installs.

For delta OTAs (zchunk; only ships the changed chunks), the inactive
slot is updated incrementally over USB-CDC HTTP range requests. See
`meta-superbird/recipes-support/swupdate/` for the delta-handler
integration.

## Build from source

```bash
just build                  # default: bridgething (kas/bridgething.yml)
just build bridgething      # the same
just build superbird        # BSP-only, no bridgething
just build example-kiosk    # BSP + chromium kiosk + placeholder webapp
just build bridgething-local  # bridgething from an unpushed local checkout
```

Cold builds pull heavily from `http://yocto.24hgr.love/` (configured in
`kas/base.yml`), so first build is mostly download-bound. Subsequent
builds reuse the local sstate + ccache.

```bash
KAS_CONTAINER_ENGINE=podman just build    # if you don't want docker
```

Output: `build/tmp/deploy/images/superbird/`. Each image produces a
flashthing zip:

- `bridgething-prod-image-superbird-flashthing.zip`
- `bridgething-dev-image-superbird-flashthing.zip`
- `superbird-bsp-image-superbird-flashthing.zip`
- `superbird-kiosk-example-image-superbird-flashthing.zip`

## Flash from local build

Drop the device into amlogic mask-rom usb (`1b8e:c003`): hold the wheel-click
while plugging in USB for a cold first flash, or `just reboot-to-maskrom`
from a booted device. The kernel writes the maskrom magic to
`PREG_STICKY_REG3` (`syscon-reboot-mode` in the board DTS) and u-boot's
`carthing_boot_route` catches it on the next boot.

```bash
just flash bridgething-prod-image
just flash bridgething-dev-image
just flash superbird-bsp-image
```

`just boot-kernel` resets the ram-staged mask-rom u-boot so the device
cold-boots the freshly written on-disk image.

If only `uboot.env` changed (no rootfs / kernel rebuild), use the
env-only zip:

```bash
just flash-env bridgething-prod-image      # ~2s vs 30-60s for a full reflash
```

## Build prerequisites

| Tool                                                        | Why                                 |
| ----------------------------------------------------------- | ----------------------------------- |
| `docker` (or `podman`)                                      | runs the kas container              |
| [`kas`](https://kas.readthedocs.io/)                        | invoked via `kas-container` wrapper |
| [`just`](https://github.com/casey/just)                     | drives the Justfile recipes         |
| [`flashthing-cli`](https://crates.io/crates/flashthing-cli) | host-side burn-mode flasher         |

For device interaction:

| Tool                  | Why                                                                               |
| --------------------- | --------------------------------------------------------------------------------- |
| `uv`                  | runs the PEP-723 Python helpers in `scripts/`                                     |
| `nmcli`               | sets up the USB-CDC-NCM profile so the device is reachable at `bridgething.local` |
| `avahi-daemon`        | mDNS resolves `bridgething.local`; the raw IP varies per serial                   |
| `ssh`, `rsync`, `scp` | device-side helpers shell out to these                                            |

Linux: user needs `dialout` (Ubuntu/Debian) or `uucp` (Arch) group for
`/dev/ttyUSB*` (FT232 UART).

## Layer layout

- `meta-superbird/` — BSP: kernel + DTS + patches, mainline u-boot 2026.07 + signed FIP, distro `superbird`, partition + perf + identity knobs, baseline systemd units, USB gadget, bluetooth. See `meta-superbird/README.md`.
- `meta-bridgething/` — Application: bridgething daemon, hub + stock webapps, OTA wrappers, prod/dev image recipes, distro `bridgething` (inherits `superbird`). See `meta-bridgething/README.md`.
- `examples/meta-superbird-kiosk-example/` — Fork-template for non-bridgething kiosks.

## Scripts

`scripts/` holds the Python and Bash helpers `just` invokes. They run
from the repo path (no install) and work standalone too:

```bash
scripts/superbird-ssh 'uname -a'
scripts/superbird-console.sh start
SUPERBIRD_HOST=bridgething.local scripts/bridgething-cdp 9222
```

Environment overrides:

| Env var                | Default                                | Purpose                            |
| ---------------------- | -------------------------------------- | ---------------------------------- |
| `KAS_CONTAINER_ENGINE` | `docker`                               | container engine for kas-container |
| `FLASHTHING_CLI`       | `flashthing-cli` (PATH)                | host-side burn-mode flasher        |
| `SUPERBIRD_HOST`       | `bridgething.local`                    | device address (mDNS)              |
| `SUPERBIRD_UART_DEV`   | first FT232 by-id, then `/dev/ttyUSB0` | UART serial node                   |
| `SUPERBIRD_RESET_HOLD` | `scripts/superbird-reset-hold.py`      | RTS reset helper                   |

## License

MIT. Upstream layers: meta-meson is GPL-2.0, openembedded-core / meta-yocto / poky are MIT.
