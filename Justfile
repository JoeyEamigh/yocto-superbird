# yocto-superbird Justfile

default := "bridgething"
export KAS_CONTAINER_ENGINE := env_var_or_default('KAS_CONTAINER_ENGINE', 'docker')

# kas-container calls `realpath` with GNU-only flags (-e, -qe, --relative-base)
# that BSD /bin/realpath rejects. scripts/macos-shims/ carries a GNU-realpath
# shim; prepend it on macOS so kas-container resolves `realpath` to grealpath.
# Empty branch on Linux = PATH untouched.
macos_shim_dir := if os() == "macos" { justfile_directory() / "scripts" / "macos-shims" } else { "" }
export PATH := if macos_shim_dir == "" { env_var('PATH') } else { macos_shim_dir + ":" + env_var('PATH') }

# macOS: the bitbake build tree lives in a Docker NAMED VOLUME, not on the Mac
# filesystem. Bind-mounting a host dir is impossible - Docker Desktop's
# libkrun/virtiofs fails every flock() bitbake relies on (confirmed 200/200 EIO)
# and presents bind-mount roots as root:root. A named volume is real ext4 inside
# the Docker VM: reliable, case-sensitive, persistent. Empty on Linux (the build
# stays in the in-repo build/).
build_vol := if os() == "macos" { "carthing-yocto" } else { "" }

# Extra `docker run` args kas-container needs on macOS:
#  - mount the build volume at /build (kas's entrypoint only chowns its known
#    managed dirs - /build, /work, ... - so the volume MUST land on one of them);
#  - KAS_DOCKER_ROOTLESS=1 triggers that in-container chown of the root-owned
#    volume mount root, so bitbake's os.access(TOPDIR, W_OK) preflight passes for
#    the non-root build user (the chown only touches the container's view, never
#    host ownership);
#  - TOPDIR=/build/build (not /build) so ccache's ${TOPDIR}/../ccache resolves to
#    /build/ccache *inside* the writable volume - with TOPDIR=/build it escapes
#    to /ccache on the container root and ccache dies "Permission denied". This
#    mirrors Linux's /work/build TOPDIR + /work/ccache sibling exactly.
# Empty on Linux.
macos_runtime_args := if os() == "macos" { "-e KAS_DOCKER_ROOTLESS=1 -e KAS_BUILD_DIR=/build/build -v " + build_vol + ":/build" } else { "" }

# Host-side build dir. On Linux this IS the live build tree (override with
# YOCTO_BUILD_DIR). On macOS the build tree is the named volume; this is only
# where `just build` mirrors deploy artifacts (images/.swu/flashthing zips) out
# of the volume so they land on the Mac disk for flashing/distribution.
export KAS_BUILD_DIR := if os() == "macos" { "" } else { env_var_or_default('YOCTO_BUILD_DIR', '') }
build_dir := if KAS_BUILD_DIR == "" { justfile_directory() / "build" } else { KAS_BUILD_DIR }

flashthing := env_var_or_default('FLASHTHING_CLI', 'flashthing-cli')

# --- Build ---

# One-time macOS host setup (idempotent): GNU coreutils (for the realpath shim)
# + the Docker named volume that holds the build tree.
[macos]
macos-setup:
  #!/usr/bin/env bash
  set -euo pipefail
  command -v grealpath >/dev/null 2>&1 || { echo "Installing coreutils (GNU realpath)..."; brew install coreutils; }
  if docker volume inspect {{build_vol}} >/dev/null 2>&1; then
    echo "Docker build volume '{{build_vol}}' already exists"
  else
    echo "Creating Docker build volume '{{build_vol}}'..."
    docker volume create {{build_vol}} >/dev/null
  fi
  echo "OK - 'just build' builds in volume '{{build_vol}}' and mirrors deploy artifacts to {{build_dir}}/tmp/deploy"

# Fetch/checkout layers
checkout target=default:
  kas-container checkout kas/{{target}}.yml

# Build the named image set inside the kas container.
build target=default:
  #!/usr/bin/env bash
  set -euo pipefail
  runtime_args="{{macos_runtime_args}}"
  if [ "$(uname)" = "Darwin" ]; then
    docker volume inspect {{build_vol}} >/dev/null 2>&1 || docker volume create {{build_vol}} >/dev/null
  elif [ -n "${KAS_BUILD_DIR:-}" ]; then
    mkdir -p "$KAS_BUILD_DIR"
  fi
  if [ "{{target}}" = "bridgething-local" ]; then
    if [ ! -f kas/bridgething-local.yml ]; then
      echo "kas/bridgething-local.yml missing - copy kas/bridgething-local.example.yml and edit BRIDGETHING_LOCAL" >&2
      exit 1
    fi
    local_dir=$(sed -n 's/.*BRIDGETHING_LOCAL[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' kas/bridgething-local.yml | head -n1)
    if [ -z "$local_dir" ] || [ ! -d "$local_dir" ]; then
      echo "BRIDGETHING_LOCAL in kas/bridgething-local.yml is missing or not a directory: '$local_dir'" >&2
      exit 1
    fi
    runtime_args="$runtime_args -v $local_dir:$local_dir"
  fi
  # Opt-in (YOCTO_LOWMEM=1): layer the memory-bounded parallelism knobs over the
  # build config. Off by default - full parallelism builds fine on a 48 GB VM.
  kas_files="kas/{{target}}.yml"
  [ -n "${YOCTO_LOWMEM:-}" ] && kas_files="${kas_files}:kas/macos-lowmem.yml"
  if [ -n "$runtime_args" ]; then
    kas-container --runtime-args "$runtime_args" build "$kas_files"
  else
    kas-container build "$kas_files"
  fi
  # macOS: lift deploy artifacts out of the build volume onto the Mac disk.
  [ "$(uname)" = "Darwin" ] && just pull-deploy || true

# Drop into a bitbake shell inside the container.
shell target=default:
  #!/usr/bin/env bash
  set -euo pipefail
  kas_files="kas/{{target}}.yml"
  if [ "$(uname)" = "Darwin" ]; then
    docker volume inspect {{build_vol}} >/dev/null 2>&1 || docker volume create {{build_vol}} >/dev/null
  fi
  [ -n "${YOCTO_LOWMEM:-}" ] && kas_files="${kas_files}:kas/macos-lowmem.yml"
  if [ -n "{{macos_runtime_args}}" ]; then
    kas-container --runtime-args "{{macos_runtime_args}}" shell "$kas_files"
  else
    kas-container shell "$kas_files"
  fi

# macOS only: mirror deploy artifacts (images, .swu, flashthing zips) out of the
# build volume onto the Mac disk at build_dir/tmp/deploy, where `just flash`
# looks. Plain file copies (no flock), so virtiofs handles them fine. Run
# automatically by `just build`; also useful standalone.
[macos]
pull-deploy:
  #!/usr/bin/env bash
  set -euo pipefail
  mkdir -p "{{build_dir}}/tmp/deploy"
  docker run --rm -v {{build_vol}}:/build -v "{{build_dir}}/tmp/deploy":/out alpine sh -c '
    if [ ! -d /build/build/tmp/deploy ]; then echo "no deploy dir in volume {{build_vol}} yet"; exit 0; fi
    apk add --no-cache rsync >/dev/null 2>&1
    rsync -a --delete /build/build/tmp/deploy/ /out/
    echo "mirrored $(find /out -type f | wc -l | tr -d " ") files"'
  echo "deploy artifacts on disk at {{build_dir}}/tmp/deploy"

# Wipe local bitbake output. Layer clones + ccache survive.
clean-build:
  #!/usr/bin/env bash
  set -euo pipefail
  if [ "$(uname)" = "Darwin" ]; then
    # Remove TOPDIR inside the volume but keep the sibling /build/ccache (mirrors
    # the Linux `rm -rf build` which preserves /work/ccache). Then drop the host
    # deploy mirror.
    docker run --rm -v {{build_vol}}:/build alpine rm -rf /build/build
    rm -rf "{{build_dir}}"
  else
    rm -rf {{build_dir}}
  fi

# Drop poky-layout symlinks under sources/ for the vscode bitbake extension.
vscode-setup:
  test -d sources/openembedded-core || just checkout
  ln -sfn openembedded-core/meta sources/meta
  ln -sfn meta-yocto/meta-poky   sources/meta-poky
  @echo "symlinks ready: sources/{meta,meta-poky}"

# --- Cache server ---

cache_host    := env_var_or_default('YOCTO_CACHE_HOST', '10.1.10.10')
cache_port    := env_var_or_default('YOCTO_CACHE_RSYNC_PORT', '8733')

push-sstate:
  #!/usr/bin/env bash
  set -euo pipefail
  # Needs the 24HGR VPN up on the host (container traffic NATs through the Mac).
  if [ "$(uname)" = "Darwin" ]; then
    # sstate lives in the build volume - rsync straight from it via a container.
    docker run --rm -v {{build_vol}}:/build alpine sh -c '
      apk add --no-cache rsync >/dev/null 2>&1
      rsync -ah --info=progress2 --ignore-existing --partial --append-verify \
        --port={{cache_port}} /build/build/sstate-cache/ rsync://{{cache_host}}/sstate/'
  else
    rsync -ah --info=progress2 --ignore-existing --partial --append-verify \
      --port={{cache_port}} \
      {{build_dir}}/sstate-cache/ \
      rsync://{{cache_host}}/sstate/
  fi

# --- Flash ---

# Flash a full image to the device.
flash image="bridgething-dev-image":
  {{flashthing}} {{build_dir}}/tmp/deploy/images/superbird/{{image}}-superbird-flashthing.zip

# Env-only reflash.
flash-env image="bridgething-dev-image":
  {{flashthing}} {{build_dir}}/tmp/deploy/images/superbird/{{image}}-superbird-flashthing-env-only.zip

# --- Release / install (no-build dev path) ---

release *args:
  scripts/superbird-release {{args}}

publish variant="prod":
  scripts/superbird-publish {{variant}}

# Pull latest dev image from ota manifest and flash it.
install-dev:
  scripts/superbird-install dev

install-prod:
  scripts/superbird-install prod

# --- Device helpers ---

# SSH into the device over USB-CDC-NCM. Splits args - watch out for quoting
ssh *args:
  scripts/superbird-ssh {{args}}

# UART console agent. Subcommand: start | stop | restart | status.
console subcmd="status":
  scripts/superbird-console.sh {{subcmd}}

# Send a single command via the uart console agent.
cmd *args:
  scripts/superbird-cmd.sh {{args}}

# Hold the FT232 RTS line deasserted (reset released) for the lifetime of this command. Foreground
reset-hold:
  scripts/superbird-reset-hold.py

# One-shot reset pulse.
reset-pulse duration_ms="200":
  scripts/superbird-reset-hold.py --pulse --duration-ms {{duration_ms}}

# Exit mask-rom usb mode and cold-boot into the on-disk image.
boot-kernel:
  scripts/superbird-boot-kernel.sh

# Reboot a running device into amlogic mask-rom usb mode (1b8e:c003) for flashthing-cli.
reboot-to-maskrom:
  scripts/superbird-reboot-to-maskrom

# Reboot a running device into u-boot fastboot (env / partition writes without a full wic flash).
reboot-to-fastboot:
  scripts/superbird-reboot-to-fastboot

# Write a single gpt partition over u-boot fastboot (skips the full wic flash).
flash-fast partlabel file="":
  scripts/superbird-flash-fast {{partlabel}} {{file}}

# Push a webapp bundle into /var/bridgething/webapps/<name>/.
push-webapp local name="":
  scripts/bridgething-push-webapp {{local}} {{name}}

# SSH-tunnel chromium's CDP from the device's 127.0.0.1:9222 to the host.
cdp port="9223":
  scripts/bridgething-cdp {{port}}

# Delta-OTA from a booted device.
ota *args:
  scripts/bridgething-ota {{args}}
