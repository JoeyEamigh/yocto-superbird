# yocto-superbird build/flash/device helpers. Builds run in the kas
# container, so the host needs no Yocto toolchain.
#   bridgething (kas/bridgething.yml) - full image: kernel + daemon + chromium kiosk
#   bsp         (kas/bsp.yml)         - kernel + busybox + ssh only

default := "bridgething"

# docker / podman both work.
export KAS_CONTAINER_ENGINE := env_var_or_default('KAS_CONTAINER_ENGINE', 'docker')

# flashthing-cli binary; override to point at a local debug build.
flashthing := env_var_or_default('FLASHTHING_CLI', 'flashthing-cli')

# --- Build ---

# Fetch/checkout layers (no build).
checkout target=default:
  kas-container checkout kas/{{target}}.yml

# Build the named image set in the kas container. Cold build pulls from
# the public sstate+downloads mirror (kas/base.yml); later builds hit the
# local sstate-cache + ccache.
#
# dev-local swaps the daemon recipes to a host bridgething checkout via
# externalsrc, then bind-mounts that host path at the same path inside the
# container so externalsrc resolves identically on both sides. The path
# lives only in the gitignored kas/dev-local.yml.
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
  # Private ThingLabsOSS repos (superbird-uboot, superbird-fip-tools) need
  # github creds inside the container. KAS_GIT_CREDENTIAL_STORE points at a
  # git credential-store file (default below); kas-container bind-mounts it
  # read-only with an absolute --file path so it survives the fetcher's
  # temp-HOME. Mint from your gh login:
  #   printf 'https://USER:%s@github.com\n' "$(gh auth token)" > "$cred"; chmod 600 "$cred"
  cred="${KAS_GIT_CREDENTIAL_STORE:-$HOME/.config/superbird-kas.git-credentials}"
  if [ -f "$cred" ]; then
    args+=(--git-credential-store "$cred")
  fi
  kas-container "${args[@]}" build kas/{{target}}.yml

# Bitbake shell in the container (devshell, manifest/dependency inspection).
shell target=default:
  kas-container shell kas/{{target}}.yml

# Wipe local bitbake output. Layer clones + downloads cache survive.
clean-build:
  rm -rf build

# Drop poky-layout symlinks under sources/ for the vscode bitbake extension
# (its pokyFolder = bitbake/.. expects meta/ + meta-poky/ there). sources/ is
# gitignored, so re-run after a fresh checkout or `kas-container purge`.
vscode-setup:
  test -d sources/openembedded-core || just checkout
  ln -sfn openembedded-core/meta sources/meta
  ln -sfn meta-yocto/meta-poky   sources/meta-poky
  @echo "symlinks ready: sources/{meta,meta-poky}"

# --- Cache server ---
# The cache server ships only sstate (content-addressed, never updated in
# place); source tarballs / git mirrors stay on upstream URLs. Point is to
# spare other builders the multi-hour chromium/wpewebkit/cog compile.
# Override host/port via the YOCTO_CACHE_* env vars.

cache_host    := env_var_or_default('YOCTO_CACHE_HOST', '10.1.10.10')
cache_port    := env_var_or_default('YOCTO_CACHE_RSYNC_PORT', '8733')

# Push local sstate up after a successful build. --ignore-existing skips
# hashes already on the server.
push-sstate:
  rsync -ah --info=progress2 --ignore-existing --partial --append-verify \
    --port={{cache_port}} \
    build/sstate-cache/ \
    rsync://{{cache_host}}/sstate/

# --- Flash ---

# Flash a full image (device in burn mode, 1b8e:c003). `just boot-kernel`
# exits burn mode into the new kernel afterward.
flash image="bridgething-dev-image":
  {{flashthing}} build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing.zip

# Env-only reflash (~2s). For when you only changed env.txt.
flash-env image="bridgething-dev-image":
  {{flashthing}} build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing-env-only.zip

# --- Release / install (no-build dev path) ---
# For bridgething devs who just want a recent image to iterate against
# without the full Yocto toolchain. Producer runs `release`/`publish` after
# a clean build; consumer runs `install-dev`/`install-prod` from this repo.

# Push the latest dev+prod flashthing zips, .swu and .zck to R2 under the
# layout the swift companion expects. Scope with dev/prod/all. Needs rclone
# with an [r2] S3 remote; see scripts/superbird-release for env knobs.
release *args:
  scripts/superbird-release {{args}}

# Full iteration loop: upload artifacts, recompose the first-flash bundle,
# regenerate + republish the discover manifest. Overwrites the matching
# (channel, image-version) slot on R2. Default prod; pass dev for the dev
# channel. Needs SUPERBIRD_RELEASE_VERSION; see scripts/superbird-publish.
publish variant="prod":
  scripts/superbird-publish {{variant}}

# Pull the latest dev image from the OTA manifest and flash it (no Yocto
# build). Needs curl + jq + flashthing-cli; device in burn mode. Run
# `just boot-kernel` after to exit burn mode.
install-dev:
  scripts/superbird-install dev

# Same as install-dev but the prod image variant.
install-prod:
  scripts/superbird-install prod

# --- Device helpers ---
# Scripts live in scripts/ (canonical). Override SUPERBIRD_HOST /
# SUPERBIRD_UART_DEV / SUPERBIRD_TOOL_DIR via env. SUPERBIRD_TOOL_DIR is a
# clone of bishopdynamics' superbird-tool (only `boot-kernel` uses it).

# SSH into the device over USB-CDC-ECM (defaults to bridgething.local via
# mDNS; the raw IP varies per device). Pass through commands/scp targets.
# Multi-device: SUPERBIRD_HOST=bridgething-<short-serial>.local.
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

# Boot the Superbird into our mainline kernel ONCE (device in burn mode,
# 1b8e:c003). The way you exit burn mode after a flash.
boot-kernel:
  scripts/superbird-boot-kernel.sh

# Drop a running device back into USB burn mode for the next boot
# (fw_setenv from libubootenv-bin; the explicit "I want to flash" path).
reboot-to-burn:
  scripts/superbird-reboot-to-burn

# Push a webapp bundle into /var/bridgething/webapps/<name>/. Default
# name = basename of <local>.
push-webapp local name="":
  scripts/bridgething-push-webapp {{local}} {{name}}

# SSH-tunnel chromium's CDP from the device's 127.0.0.1:9222 to the host.
# chromium >= M111 silently ignores --remote-debugging-address=non-localhost,
# so this tunnel is the only path to chrome://inspect from the host.
cdp port="9223":
  scripts/bridgething-cdp {{port}}

# Delta-OTA from a booted device: drives `host-gateway push-update` against
# the device gateway (OtaBegin / OtaChunk / .zck range proxy); the daemon
# owns install + reboot. Defaults to dev; pass --image prod. Much faster
# than the burn-mode + flashthing loop.
ota *args:
  scripts/bridgething-ota {{args}}
