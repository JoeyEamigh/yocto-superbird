# yocto-superbird

A Yocto image stack for the Spotify Car Thing (Superbird, Amlogic
S905D2). The BSP runs a mainline Linux kernel (v7.x with a small patch
stack for the panel, BT, touchscreen, and rotary encoder) under mainline
u-boot 2026.07 with a signed FIP. You can build the bare BSP, a
chromium kiosk example, or the bridgething stack.

## Image variants

| Image                        | What you get                                                       | Use for                                             |
| ---------------------------- | ------------------------------------------------------------------ | --------------------------------------------------- |
| `superbird-bsp-image`        | kernel, busybox, sshd, USB-CDC gadget                              | bring-up; starting point for your own userspace     |
| `superbird-kiosk-prod-image` | ext4 ro rootfs, chromium kiosk, example daemon, placeholder webapp | fork-template for any chromium kiosk (prod variant) |
| `superbird-kiosk-dev-image`  | squashfs-lz4 rootfs, weston desktop + VNC + dev tools              | fork-template for any chromium kiosk (dev variant)  |
| `bridgething-prod-image`     | ext4 ro rootfs, chromium kiosk, bridgething daemon, OTA            | the bridgething project's own production build      |
| `bridgething-dev-image`      | squashfs-lz4 rootfs with weston desktop, VNC, dev tools            | bridgething iteration                               |

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
public sstate mirror that primes most of the build for you.

```bash
KAS_CONTAINER_ENGINE=podman just build superbird   # if you don't run docker, otherwise omit the env var
```

### macOS (Apple Silicon)

The build runs in the same kas container on macOS. Run the one-time
setup first (needs Homebrew and a running Docker Desktop):

```bash
just macos-setup    # installs GNU coreutils + creates the Docker build volume
```

Then `just build <target>` works as above.

- **The build tree lives in a Docker named volume** (`carthing-yocto`). bitbake needs a case-sensitive filesystem and POSIX file locking, neither of which Docker
  Desktop's virtiofs provides over a bind-mounted host directory.

`just clean-build` wipes the build tree in the volume but keeps ccache;
`docker volume rm carthing-yocto` removes everything.

Output lands in `build/tmp/deploy/images/superbird/`. Each image
produces a flashthing zip:

- `superbird-bsp-image-superbird-flashthing.zip`
- `superbird-kiosk-{prod,dev}-image-superbird-flashthing.zip`
- `bridgething-{prod,dev}-image-superbird-flashthing.zip`

## Flash

Put the device into Amlogic mask-rom USB (`1b8e:c003`): hold buttons
1+4 while plugging in USB. From a booted device.

```bash
just flash superbird-bsp-image
just flash superbird-kiosk-dev-image
just flash bridgething-prod-image
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

## Layer layout

- `meta-superbird/` is the BSP: kernel, DTS, mainline u-boot, partition geometry, baseline systemd units, USB gadget, bluetooth, `superbird-init`, `packagegroup-superbird-runtime`, the `superbird-image` and `bandaid-image` bbclasses. See `meta-superbird/README.md`.
- `meta-bridgething/` is the bridgething application layer: the daemon, hub and stock webapps, OTA wrappers, prod/dev image recipes. See `meta-bridgething/README.md`.
- `examples/meta-superbird-kiosk-example/` is the fork-template for a non-bridgething kiosk: distro + prod and dev images + bandaid + minimal C daemon + OTA wrappers. See its own README for the fork checklist.

## Host tools

| Tool                                                        | Why                                  |
| ----------------------------------------------------------- | ------------------------------------ |
| `docker` or `podman`                                        | runs the kas container               |
| [`kas`](https://kas.readthedocs.io/)                        | invoked through `kas-container`      |
| [`just`](https://github.com/casey/just)                     | drives the recipes in the `Justfile` |
| [`flashthing-cli`](https://crates.io/crates/flashthing-cli) | host-side burn-mode flasher          |

For device interaction you'll also want `ssh`, `avahi-daemon` (mDNS),
and on Linux your user in the `dialout` (Debian/Ubuntu) or `uucp` (Arch)
group so you can open `/dev/ttyUSB*`.

## License

MIT. Upstream layers: meta-meson is GPL-2.0; openembedded-core,
meta-yocto, and poky are MIT.
