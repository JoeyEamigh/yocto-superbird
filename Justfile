# yocto-superbird Justfile

default := "bridgething"
export KAS_CONTAINER_ENGINE := env_var_or_default('KAS_CONTAINER_ENGINE', 'docker')

# kas-container calls `realpath` with GNU-only flags
macos_shim_dir := if os() == "macos" { justfile_directory() / "scripts" / "macos-shims" } else { "" }
export PATH := if macos_shim_dir == "" { env_var('PATH') } else { macos_shim_dir + ":" + env_var('PATH') }

# macOS: the bitbake build tree lives in a Docker NAMED VOLUME, not on the local filesystem
build_vol := if os() == "macos" { "carthing-yocto" } else { "" }

# Extra `docker run` args kas-container needs on macOS:
#  - mount the build volume at /build
#  - KAS_DOCKER_ROOTLESS=1 triggers in-container chown of the root-owned volume mount
#  - TOPDIR=/build/build so ccache's ${TOPDIR}/../ccache resolves to /build/ccache inside the writable volume
macos_runtime_args := if os() == "macos" { "-e KAS_DOCKER_ROOTLESS=1 -e KAS_BUILD_DIR=/build/build -v " + build_vol + ":/build" } else { "" }

# Host-side build dir
export KAS_BUILD_DIR := if os() == "macos" { "" } else { env_var_or_default('YOCTO_BUILD_DIR', '') }
build_dir := if KAS_BUILD_DIR == "" { justfile_directory() / "build" } else { KAS_BUILD_DIR }

flashthing := env_var_or_default('FLASHTHING_CLI', 'flashthing-cli')

# --- Build ---

# One-time macOS host setup (idempotent): GNU coreutils + the Docker named volume
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
  case "{{target}}" in
    *-local)
      if [ ! -f kas/{{target}}.yml ]; then
        echo "kas/{{target}}.yml missing - copy kas/{{target}}.example.yml and edit BRIDGETHING_LOCAL" >&2
        exit 1
      fi
      local_dir=$(sed -n 's/.*BRIDGETHING_LOCAL[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' kas/{{target}}.yml | head -n1)
      if [ -z "$local_dir" ] || [ ! -d "$local_dir" ]; then
        echo "BRIDGETHING_LOCAL in kas/{{target}}.yml is missing or not a directory: '$local_dir'" >&2
        exit 1
      fi
      runtime_args="$runtime_args -v $local_dir:$local_dir"
      # virtiofs cannot list a 100k-entry cargo target dir; the container builds into the volume anyway
      if [ "$(uname)" = "Darwin" ]; then
        for d in "$local_dir"/target*/; do
          [ -d "$d" ] && runtime_args="$runtime_args --tmpfs ${d%/}"
        done
      fi
      ;;
  esac
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

# macOS only: mirror deploy artifacts (images, .swu, flashthing zips) out of the build volume onto the Mac disk at build_dir/tmp/deploy
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

# --- Tests ---

# Drive the on-device shell scripts against scratch trees
test-scripts:
  #!/usr/bin/env bash
  set -euo pipefail
  scripts/test-adopt-daemon
  if docker info >/dev/null 2>&1; then
    echo "--- busybox 1.37.0 ---"
    docker run --rm -v "$PWD:/w:ro" -w /w busybox:1.37.0 sh scripts/test-adopt-daemon
    echo "--- superbird-fsck ---"
    docker run --rm --privileged -e DEBIAN_FRONTEND=noninteractive -v "$PWD:/w:ro" -w /w debian:trixie-slim bash -c \
      'apt-get update -qq >/dev/null && apt-get install -y -qq e2fsprogs util-linux >/dev/null && exec scripts/test-superbird-fsck'
  else
    echo "docker is down: skipped busybox and superbird-fsck" >&2
  fi

# --- sstate mirror ---

r2_endpoint := "https://0a665ba1f35a38354b3f623be13f14bd.r2.cloudflarestorage.com"
sstate_remote := "r2:bridgething-sstate/sstate"
r2_creds := env_var_or_default('CARTHING_R2_CREDS', '/tmp/carthing-r2-creds.env')

# Upload this build's new sstate objects to the public mirror.
push-sstate:
  #!/usr/bin/env bash
  set -euo pipefail
  source scripts/sstate-rclone-env {{r2_creds}} {{r2_endpoint}}
  args=(copy --ignore-existing --fast-list --transfers 8 --checkers 16 --stats 30s --stats-one-line
        --exclude '*.lock' --exclude 'sstate:bridgething-daemon:*' --exclude 'sstate:bridgething-webapps:*')
  manifest=$(mktemp)
  trap 'rm -f "$manifest"' EXIT
  stamp_hashes='find /build/build/tmp/stamps {{build_dir}}/tmp/stamps -type f \( -name "*.do_*.sigdata.*" -o -name "*.do_*_setscene.*" \) 2>/dev/null | sed -E "s/.*(sigdata|_setscene)\.//; s/\..*//" | sort -u'
  if [ "$(uname)" = "Darwin" ]; then
    docker run --rm -v {{build_vol}}:/build "${RCLONE_DOCKER_ENV[@]}" rclone/rclone \
      "${args[@]}" /build/build/sstate-cache {{sstate_remote}}
    docker run --rm -v {{build_vol}}:/build alpine sh -c "$stamp_hashes" > "$manifest"
    arch=$(docker run --rm alpine uname -m)
  else
    rclone "${args[@]}" {{build_dir}}/sstate-cache {{sstate_remote}}
    sh -c "$stamp_hashes" > "$manifest"
    arch=$(uname -m)
  fi
  [ -s "$manifest" ] || { echo "no stamps under tmp/stamps; not publishing a manifest" >&2; exit 1; }
  # the manifest is what the mirror's garbage collector keeps; only a green build should publish one
  rclone copyto "$manifest" "r2:bridgething-sstate/manifests/$arch/$(date -u +%Y%m%dT%H%M%SZ).txt"
  echo "published manifest: $(wc -l < "$manifest" | tr -d ' ') live hashes for $arch" >&2

# --- Flash ---

# Flash a full image to the device.
flash image="bridgething-dev-image":
  {{flashthing}} {{build_dir}}/tmp/deploy/images/superbird/{{image}}-superbird-flashthing.zip

# Env-only reflash.
flash-env image="bridgething-dev-image":
  {{flashthing}} {{build_dir}}/tmp/deploy/images/superbird/{{image}}-superbird-flashthing-env-only.zip

# --- Release / install (no-build dev path) ---

# Pin the published daemon the image installs
pin-daemon *args:
  scripts/bridgething-pin-daemon {{args}}

# Pin the published webapps the image installs
pin-webapps:
  scripts/bridgething-pin-webapps

# Pin the published wake-word runtime and phrase model the image installs
pin-wakeword *args:
  scripts/bridgething-pin-wakeword {{args}}

# Pull latest dev image from ota manifest and flash it.
install-dev:
  scripts/superbird-install dev

install-prod:
  scripts/superbird-install prod

# --- Device helpers ---

# SSH into the device over USB-CDC-NCM
ssh *args:
  scripts/superbird-ssh {{quote(args)}}

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

# SSH-tunnel chromium's CDP to the host. Normally reachable at bridgething.local:9222 without this.
cdp port="9223":
  scripts/bridgething-cdp {{port}}

# Delta-OTA from a booted device.
ota *args:
  scripts/bridgething-ota {{args}}
