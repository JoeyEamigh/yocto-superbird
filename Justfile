# yocto-superbird Justfile
#
# Two image flavors live here:
#   - bridgething (kas/bridgething.yml): kernel + bridgething daemon +
#     chromium kiosk. The full Spotify-replacement image.
#   - bsp        (kas/bsp.yml):          kernel + busybox + ssh only.
#     Minimum useful BSP for kernel hacking or as a base for non-
#     bridgething userspace.
#
# Builds run inside the official kas container so the host doesn't
# need a Yocto-grade toolchain. Default container engine is docker;
# override with KAS_CONTAINER_ENGINE=podman.

default := "bridgething"

# Container engine kas-container will use. docker / podman both work.
export KAS_CONTAINER_ENGINE := env_var_or_default('KAS_CONTAINER_ENGINE', 'docker')

# Path to the flashthing-cli binary. `cargo install flashthing-cli`
# drops it on $PATH; override here if you want to point at a local
# checkout's debug build instead.
flashthing := env_var_or_default('FLASHTHING_CLI', 'flashthing-cli')

# --- Build ---

# Fetch/checkout layers (no build).
checkout target=default:
  kas-container checkout kas/{{target}}.yml

# Build the named image set inside the kas container. Cold first build
# pulls almost everything from yocto.24hgr.love (the public sstate +
# downloads mirror configured in kas/base.yml). Subsequent builds hit
# the local sstate-cache + ccache. Use `bsp` for a kernel-only image
# without the bridgething daemon.
#
# The `dev-local` target swaps the daemon recipes' SRC_URI for a host-
# side bridgething checkout via externalsrc. kas-container only mounts
# the kas working dir, so we additionally bind-mount the host path read
# out of kas/dev-local.yml at the same path inside the container - that
# keeps BRIDGETHING_LOCAL identical on host and inside the container so
# externalsrc resolves the same way in both places. The path lives only
# in kas/dev-local.yml (gitignored); nothing host-specific is committed.
build target=default:
  #!/usr/bin/env bash
  set -euo pipefail
  args=()
  if [ "{{target}}" = "dev-local" ]; then
    if [ ! -f kas/dev-local.yml ]; then
      echo "kas/dev-local.yml missing - copy kas/dev-local.example.yml and edit BRIDGETHING_LOCAL" >&2
      exit 1
    fi
    local_dir=$(sed -n 's/.*BRIDGETHING_LOCAL[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' kas/dev-local.yml | head -n1)
    if [ -z "$local_dir" ] || [ ! -d "$local_dir" ]; then
      echo "BRIDGETHING_LOCAL in kas/dev-local.yml is missing or not a directory: '$local_dir'" >&2
      exit 1
    fi
    args+=(--runtime-args "-v $local_dir:$local_dir")
  fi
  kas-container "${args[@]}" build kas/{{target}}.yml

# Drop into a bitbake shell inside the container. Useful for `bitbake
# -c devshell <recipe>`, manifest inspection, dependency analysis.
shell target=default:
  kas-container shell kas/{{target}}.yml

# Wipe local bitbake output. Layer clones + downloads cache survive.
clean-build:
  rm -rf build

# Drop poky-layout symlinks under sources/ so the vscode bitbake
# extension's "pokyFolder = bitbake/.." assumption finds meta/ and
# meta-poky/. sources/ is gitignored, so re-run after a fresh checkout
# (or after `kas-container purge`).
vscode-setup:
  test -d sources/openembedded-core || just checkout
  ln -sfn openembedded-core/meta sources/meta
  ln -sfn meta-yocto/meta-poky   sources/meta-poky
  @echo "symlinks ready: sources/{meta,meta-poky}"

# --- Cache server ---
# Push local sstate-cache up to the rsync daemon at the cache server.
# The cache server only ships sstate (compiled artifacts) - source
# tarballs / git mirrors stay on upstream URLs. The intent is to
# spare other builders the multi-hour chromium / wpewebkit / cog
# compile; downloads link rot is rare enough that the 50+ GB DL_DIR
# isn't worth permanent hosting.
#
# --ignore-existing skips hashes already on the server (sstate is
# content-addressed and never updates in place). Override host/port
# via the YOCTO_CACHE_* env vars for a different cache target.

cache_host    := env_var_or_default('YOCTO_CACHE_HOST', '10.1.10.10')
cache_port    := env_var_or_default('YOCTO_CACHE_RSYNC_PORT', '8733')

# Push the locally-produced sstate-cache to the cache server. Run
# this after a successful local build so the next cold build (yours
# or anyone else's downstream) hits the mirror instead of rebuilding
# from source.
push-sstate:
  rsync -ah --info=progress2 --ignore-existing --partial --append-verify \
    --port={{cache_port}} \
    build/sstate-cache/ \
    rsync://{{cache_host}}/sstate/

# --- Flash ---

# Flash a full image to the device (~30s for squashfs prod, ~60s dev).
# Device must be in burn mode (USB enumerates as 1b8e:c003). After a
# successful flash, `just boot-kernel` exits burn mode into the new
# kernel.
flash image="bridgething-dev-image":
  {{flashthing}} build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing.zip

# Env-only reflash (~2s). For when you only changed env.txt.
flash-env image="bridgething-dev-image":
  {{flashthing}} build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing-env-only.zip

# --- Device helpers ---
# Scripts live in scripts/ as the canonical source of truth. Override
# SUPERBIRD_HOST / SUPERBIRD_UART_DEV / SUPERBIRD_TOOL_DIR via env when
# calling. SUPERBIRD_TOOL_DIR is the path to a clone of bishopdynamics'
# superbird-tool repo (only used by `boot-kernel`).

# SSH into the device over USB-CDC-ECM (10.42.1.2). Pass through any
# args (commands, scp-style targets, etc).
ssh *args:
  scripts/superbird-ssh {{args}}

# UART console agent. Subcommand: start | stop | restart | status.
console subcmd="status":
  scripts/superbird-console.sh {{subcmd}}

# Send a single u-boot command via superbird-tool's --bulkcmd. Output
# lands on UART (watch via the console agent).
cmd *args:
  scripts/superbird-cmd.sh {{args}}

# Hold the FT232 RTS line deasserted (reset released) for the lifetime
# of this command. Foreground; Ctrl-C to stop.
reset-hold:
  scripts/superbird-reset-hold.py

# One-shot reset pulse. Default 200ms is plenty for the SoC to latch.
reset-pulse duration_ms="200":
  scripts/superbird-reset-hold.py --pulse --duration-ms {{duration_ms}}

# Boot the Superbird into our mainline kernel ONCE. Device must be in
# USB burn mode (1b8e:c003). The way you exit burn mode after a flash.
boot-kernel:
  scripts/superbird-boot-kernel.sh

# Drop a running device back into USB burn mode for the next boot.
# Uses fw_setenv from libubootenv-bin (env.txt defaults to
# want_boot=kernel; this is the explicit "I want to flash" path).
reboot-to-burn:
  scripts/superbird-reboot-to-burn

# Push a daemon binary / dir into /opt/bridgething/daemon/ on the
# device. Bind-mounted from settings, survives bootslot swaps + OTA.
push-daemon local name="":
  scripts/bridgething-push-daemon {{local}} {{name}}

# Push a webapp bundle into /var/bridgething/webapps/<name>/. Default
# name = basename of <local>.
push-webapp local name="":
  scripts/bridgething-push-webapp {{local}} {{name}}

# SSH-tunnel chromium's CDP from the device's 127.0.0.1:9222 to the
# host. chromium >= M111 ignores --remote-debugging-address=non-localhost
# silently, so this tunnel is the path to chrome://inspect from the host.
cdp port="9223":
  scripts/bridgething-cdp {{port}}

# Stub HTTP server that serves the prod delta-OTA chunk file
# (system.ext2.zck) on http://10.42.1.1:8000 - what the on-device
# delta handler fetches when bridgething daemon's bridge isn't up.
delta-stub:
  scripts/superbird-delta-stub.py

# Delta-OTA from a booted device. Spawns the host delta-stub HTTP
# server, scp's the .swu manifest, runs swupdate via bridgething-ab
# apply (which writes the inactive slot + atomically flips
# active_slot via the manifest's bootenv block), then reboots.
# Defaults to the dev image (bridgething-update-dev). Pass --image
# prod for the prod variant. Skips the burn-mode-then-flashthing
# loop so iteration is way faster than `just flash`.
ota *args:
  scripts/bridgething-ota {{args}}
