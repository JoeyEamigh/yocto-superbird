# yocto-superbird

A Yocto image stack for the Spotify Car Thing (Superbird, Amlogic
S905D2). The BSP runs a mainline Linux kernel (v7.x with a small patch
stack for the panel, BT, touchscreen, and rotary encoder) under mainline
u-boot 2026.07 with a signed FIP. You can build the bare BSP, a
chromium kiosk example, or the bridgething stack.

## Image variants

| Image | What you get | Use for |
| --- | --- | --- |
| `superbird-bsp-image` | kernel, busybox, sshd, USB-CDC gadget | bring-up; starting point for your own userspace |
| `superbird-kiosk-prod-image` | ext4 ro rootfs, chromium kiosk, example daemon, placeholder webapp | fork-template for any chromium kiosk (prod variant) |
| `superbird-kiosk-dev-image` | squashfs-lz4 rootfs, weston desktop + VNC + dev tools | fork-template for any chromium kiosk (dev variant) |
| `bridgething-prod-image` | ext4 ro rootfs, chromium kiosk, bridgething daemon, OTA | the bridgething project's own production build |
| `bridgething-dev-image` | squashfs-lz4 rootfs with weston desktop, VNC, dev tools | bridgething iteration |

All share the same GPT layout (env + boot_a + root_a + boot_b +
root_b + bandaid + data) and the same OTA pipeline.

## Build

```bash
just build superbird          # BSP only
just build example-kiosk      # BSP + chromium kiosk + example daemon (prod + dev variants)
just build bridgething        # bridgething's full stack
```

The first cold build downloads a lot. Subsequent builds reuse the local
sstate-cache and ccache under `build/` and `ccache/`. There's also a
public sstate mirror at `http://yocto.24hgr.love/sstate/` (read-only,
configured in `kas/base.yml`) that primes most of the build for you.

```bash
KAS_CONTAINER_ENGINE=podman just build superbird   # if you don't run docker
```

Output lands in `build/tmp/deploy/images/superbird/`. Each image
produces a flashthing zip:

- `superbird-bsp-image-superbird-flashthing.zip`
- `superbird-kiosk-{prod,dev}-image-superbird-flashthing.zip`
- `bridgething-{prod,dev}-image-superbird-flashthing.zip`

## Flash

Put the device into Amlogic mask-rom USB (`1b8e:c003`): hold the
wheel-click button while plugging in USB. From a booted device,
`just reboot-to-maskrom` does the same without unplugging.

```bash
just flash superbird-bsp-image
just flash superbird-kiosk-dev-image
just flash bridgething-prod-image
just boot-kernel              # exit mask-rom and cold-boot the new image
```

For an env-only change (touched `uboot.env` only, no kernel or rootfs
rebuild) use the env-only zip:

```bash
just flash-env superbird-bsp-image    # ~2s vs 30-60s for a full reflash
```

## Talk to the device

Over USB-CDC-NCM, the device shows up on mDNS as `<hostname>.local`.
The BSP image defaults to `superbird.local`; the kiosk example
defaults to `superbird-kiosk.local`; bridgething's images default to
`bridgething.local`. Multi-device hosts append a short serial suffix.

```bash
just ssh                      # interactive shell
just ssh 'uname -a'           # one-shot
```

UART for pre-SSH boots or kernel panics:

```bash
just console start            # long-lived agent on /dev/ttyUSB0
just cmd 'dmesg | tail -30'
just console stop
```

The agent keeps FT232 RTS deasserted (it's wired to the SoC reset pin),
so the board doesn't reset every time another process opens the serial
node. Don't open `/dev/ttyUSB0` directly while the agent is running.

Other knobs:

```bash
just reset-pulse 200          # 200ms RTS LOW, soft reset
just reboot-to-maskrom        # drop into 1b8e:c003 for a full wic flash
just reboot-to-fastboot       # drop into u-boot fastboot
```

## OTA

OTAs are A/B with libswupdate. A successful install writes the inactive
slot, flips `slot_active` in u-boot env, and reboots; if the new slot
fails to come up three times the bootloader rolls back. The companion
app drives the `.swu` push; the on-device `bridgething-ab` binary is a
debug helper (`status`, `set-slot a|b`) and does not drive installs
itself.

Three install kinds:

- `image`: writes a full `.swu` to root_X via libswupdate
- `daemon`: aarch64 binary rotated atomically on the bandaid bind-mount, service restart
- `builtin-webapp`: hub or stock webapp zip swapped on the bandaid bind-mount, service restart

Delta OTAs (zchunk) ship only the changed chunks and apply via
HTTP range requests over the USB link. The handler integration lives
in `meta-superbird/recipes-support/swupdate/`.

## Layer layout

- `meta-superbird/` is the BSP: kernel, DTS, mainline u-boot, partition geometry, baseline systemd units, USB gadget, bluetooth, `superbird-init`, `packagegroup-superbird-runtime`, the `superbird-image` and `bandaid-image` bbclasses. See `meta-superbird/README.md`.
- `meta-bridgething/` is the bridgething application layer: the daemon, hub and stock webapps, OTA wrappers, prod/dev image recipes. See `meta-bridgething/README.md`.
- `examples/meta-superbird-kiosk-example/` is the fork-template for a non-bridgething kiosk: distro + prod and dev images + bandaid + minimal C daemon + OTA wrappers. See its own README for the fork checklist.

## Host tools

| Tool | Why |
| --- | --- |
| `docker` or `podman` | runs the kas container |
| [`kas`](https://kas.readthedocs.io/) | invoked through `kas-container` |
| [`just`](https://github.com/casey/just) | drives the recipes in the `Justfile` |
| [`flashthing-cli`](https://crates.io/crates/flashthing-cli) | host-side burn-mode flasher |

For device interaction you'll also want `ssh`, `avahi-daemon` (mDNS),
and on Linux your user in the `dialout` (Debian/Ubuntu) or `uucp` (Arch)
group so you can open `/dev/ttyUSB*`.

The Python helpers under `scripts/` use PEP-723 inline metadata. If you
have `uv` installed they Just Work; otherwise read the shebang for the
required interpreter and packages.

## License

MIT. Upstream layers: meta-meson is GPL-2.0; openembedded-core,
meta-yocto, and poky are MIT.
