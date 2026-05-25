# yocto-superbird

A Yocto-based mainline-Linux image stack for the Spotify Car Thing (Superbird,
Amlogic S905D2). Two flavors:

- **bridgething images** — kernel + Panfrost GPU + chromium kiosk + the
  bridgething daemon. The full Spotify-replacement stack.
- **superbird-bsp image** — kernel + busybox + ssh. Smallest flashable image
  on the Superbird hardware. Useful as a kernel-iteration target or a base
  for any non-bridgething userspace.

The kernel is mainline v6.19 with a small patch stack for the parts of the
hardware that aren't in upstream yet (ST7701S panel variant, BCM20703A2
Bluetooth, tlsc6x touchscreen, rotary encoder fwnode-irq fixup, a few drm
display-handoff hooks). u-boot is left stock — Yocto only owns kernel +
rootfs.

## Install (no build)

For folks working on bridgething itself (the daemon, the webapp, anything
on top of the image) and not the image itself: a rolling `latest` GitHub
release carries the most recent dev + prod flashthing zips. Pull from
that and skip the multi-hour Yocto build.

One-time setup:

```bash
gh auth login                  # gh CLI, repo scope (release is private)
cargo install flashthing-cli
sudo flashthing-cli --setup    # udev rule for libusb burn-mode access (Linux)
```

Then, from a clone of this repo, with the Car Thing in burn mode (hold
wheel-click while plugging in USB):

```bash
just install-dev               # or just install-prod
just boot-kernel               # exit burn mode into the new image
```

About 18 s after `boot-kernel`, the device is up at `bridgething.local`
(mDNS via avahi-daemon) over USB-CDC-NCM (host-side NetworkManager
profile required, see [Iteration](#iteration)) and ready for `just
push-webapp` / `just ssh`. The raw IP varies per device (per-serial /29
in 10.42.1.x); mDNS handles that for you.
Daemon binaries get pushed from the bridgething repo via `just push`
(cross-build + atomic rotate at `/opt/bridgething/daemon/bridgething.current`).

The release is updated manually after clean local builds (`just release`)
— there's no CI yet and no semver, just whatever was last pushed.

## Build prerequisites

| Tool | Why |
| --- | --- |
| `docker` (or `podman`) | runs the kas container |
| [`kas`](https://kas.readthedocs.io/) | invoked via `kas-container` wrapper |
| [`just`](https://github.com/casey/just) | drives the recipes in `Justfile` |
| [`flashthing-cli`](https://crates.io/crates/flashthing-cli) | host-side burn-mode flasher (`cargo install flashthing-cli`) |

For interacting with a connected device:

| Tool | Why |
| --- | --- |
| `uv` | runs the PEP-723 Python helper scripts in `scripts/` |
| `nmcli` | sets up the USB-CDC-NCM profile so the device is reachable at `bridgething.local` |
| `avahi-daemon` | resolves `bridgething.local` host-side; the device IP itself varies per serial (/29 subnet) |
| `ssh`, `rsync`, `scp` | the device-side helpers shell out to these |
| `bishopdynamics/superbird-tool` (cloned, exported as `SUPERBIRD_TOOL_DIR`) | only used by `just boot-kernel` for u-boot bulkcmd |

User must be in `dialout` (Ubuntu/Debian) or `uucp` (Arch) to access
`/dev/ttyUSB*` for the FT232 UART.

## Build

```bash
just build              # default: bridgething (kas/bridgething.yml)
just build bridgething  # the same
just build bsp          # BSP-only, no bridgething daemon
```

The first build pulls heavily from the public sstate + downloads mirror at
`http://yocto.24hgr.love/` (configured in `kas/base.yml`), so cold builds
are mostly download-bound rather than compile-bound. Subsequent builds reuse
the local sstate and ccache that live alongside the build dir.

Use a different container engine via:

```bash
KAS_CONTAINER_ENGINE=podman just build
```

Output lands in `build/tmp/deploy/images/superbird/`. Each image emits a
flashthing zip alongside the rootfs, e.g.:

- `bridgething-dev-image-superbird-flashthing.zip`
- `bridgething-prod-image-superbird-flashthing.zip`
- `superbird-bsp-image-superbird-flashthing.zip`

## Flash

The Car Thing must be in burn mode (USB enumerates as `1b8e:c003`). Hold
the wheel-click button while plugging in USB, or from a running device with
`just reboot-to-burn`.

```bash
just flash superbird-bsp-image
just flash bridgething-prod-image
just flash bridgething-dev-image
```

After a flash finishes, `just boot-kernel` exits burn mode into the new
kernel (env defaults to `want_boot=kernel`, so plain reboots stay there).

If only `env.txt` changed (no rootfs / kernel rebuild), use the env-only
zip:

```bash
just flash-env bridgething-dev-image
```

About 2 seconds vs 30-60 for a full reflash.

## Iteration

After the first boot the dev image brings up a USB-CDC-NCM gadget on
a per-serial /29 in the `10.42.1.x` range, advertised over mDNS as
`bridgething.local`. The device-side gadget script derives the subnet
nibble from the same serial-sha that produces its MAC, so two devices
on one host land in disjoint subnets. Host side: the existing
NetworkManager profile (DHCP via the device's own server, matched by
MAC prefix `02:11:44:*`) reaches the right subnet; mDNS resolves
`bridgething.local` to whichever IP the device landed at. Multi-device
hosts use `bridgething-<short-serial>.local` (auto-published per
device by avahi). Then:

```bash
just ssh                                  # interactive shell
just ssh 'uname -a'                       # one-shot
just ssh 'systemctl status bridgething'   # service status
```

Daemon and webapp hot-reload (dev images only — paths are bind-mounts
from the settings partition that survive bootslot swaps and OTA):

```bash
# from the bridgething repo:
just push                                  # cross-build + push the daemon
# from this repo:
just push-webapp  ./dist superbird-webapp
```

Chromium DevTools (CDP) over SSH tunnel, since chromium ≥ M111 ignores
`--remote-debugging-address`:

```bash
just cdp     # then http://localhost:9223/json/version
```

UART (kernel panic / pre-SSH boot diagnostics):

```bash
just console start    # long-lived agent on /dev/ttyUSB0
just cmd 'dmesg | tail -30'
just console stop
```

The console agent holds RTS deasserted (the FT232 RTS line is wired to
the SoC reset pin) so the board doesn't get a spurious reset every time
something opens the serial port. Don't open `/dev/ttyUSB0` with anything
else while the agent is running — it manages the port via a FIFO.

```bash
just reset-pulse 200    # one-shot soft reset (200ms)
just boot-kernel        # bulkcmd into kernel from burn mode
just reboot-to-burn     # fw_setenv want_boot=burn + systemctl reboot
```

## Layer layout

```
meta-superbird/         BSP layer: machine, kernel + DTS + patches,
                        u-boot env template, AML boot.img + flashthing
                        packaging, stock-firmware blobs, BCM20703 BT
                        recipe.
meta-bridgething/       Application layer: bridgething daemon recipe,
                        chromium / weston bbappends, kiosk launcher,
                        A/B OTA tooling (swupdate + bridgething-ab),
                        USB-gadget config, packagegroups, image
                        recipes for dev + prod.
```

`meta-superbird` doesn't depend on `meta-bridgething`, so a third party
can reuse it as a clean BSP for non-bridgething userspace.

## Image variants

| Image | Contains | Partition geometry |
| --- | --- | --- |
| `superbird-bsp-image` | kernel, busybox, openssh, USB-CDC-NCM gadget, BlueZ | stock AML MPT |
| `bridgething-prod-image` | + Mesa+Panfrost, weston, chromium kiosk, daemon, kiosk webapp, swupdate | stock AML MPT |
| `bridgething-dev-image` | prod + cog, glmark2, dev tools, persistent `/opt/bridgething` overlay, debug auth | stock AML MPT |

All three use squashfs-zst rootfs and the same A/B partition geometry, so
the OTA pipeline (`bridgething-update[-prod]`) is identical for all of them.

## OTA

```bash
just build bridgething   # produces bridgething-update-superbird.swu
just ssh 'bridgething-ab apply-and-reboot' < build/tmp/deploy/images/superbird/bridgething-update-superbird.swu
```

For the delta-OTA prod flow (zchunk-based, only ships the changed
chunks), the inactive slot is updated incrementally over USB-CDC HTTP
range requests. See `meta-bridgething/recipes-support/swupdate/` for the
delta handler integration.

## Scripts

`scripts/` holds the Python and Bash helpers `just` invokes. They run
directly from the repo path (no install step) and can also be invoked
without `just` if you prefer:

```bash
scripts/superbird-ssh 'uname -a'
scripts/superbird-console.sh start
SUPERBIRD_HOST=bridgething.local scripts/bridgething-cdp 9222
```

Environment overrides:

| Env var | Default | Purpose |
| --- | --- | --- |
| `KAS_CONTAINER_ENGINE` | `docker` | container engine for kas-container |
| `FLASHTHING_CLI` | `flashthing-cli` (PATH) | host-side burn-mode flasher |
| `SUPERBIRD_HOST` | `bridgething.local` | device address (mDNS; raw IP varies per serial because the gadget script picks a /29 from a serial-derived nibble) |
| `SUPERBIRD_UART_DEV` | first FT232 by-id, then `/dev/ttyUSB0` | UART serial node |
| `SUPERBIRD_TOOL_DIR` | (required by `just boot-kernel`) | path to bishopdynamics/superbird-tool clone |
| `SUPERBIRD_RESET_HOLD` | `scripts/superbird-reset-hold.py` | RTS reset helper (FT232) |

## License

MIT for everything authored in this repo. Upstream layers carry their own
licenses; meta-meson is GPL-2.0, openembedded-core / meta-yocto / poky are
MIT.
