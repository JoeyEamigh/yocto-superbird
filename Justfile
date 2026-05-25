# yocto-superbird Justfile

default := "bridgething"
export KAS_CONTAINER_ENGINE := env_var_or_default('KAS_CONTAINER_ENGINE', 'docker')
flashthing := env_var_or_default('FLASHTHING_CLI', 'flashthing-cli')

# --- Build ---

# Fetch/checkout layers
checkout target=default:
  kas-container checkout kas/{{target}}.yml

# Build the named image set inside the kas container.
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

# Drop into a bitbake shell inside the container.
shell target=default:
  kas-container shell kas/{{target}}.yml

# Wipe local bitbake output. Layer clones + downloads cache survive.
clean-build:
  rm -rf build

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
  rsync -ah --info=progress2 --ignore-existing --partial --append-verify \
    --port={{cache_port}} \
    build/sstate-cache/ \
    rsync://{{cache_host}}/sstate/

# --- Flash ---

# Flash a full image to the device.
flash image="bridgething-dev-image":
  {{flashthing}} build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing.zip

# Env-only reflash.
flash-env image="bridgething-dev-image":
  {{flashthing}} build/tmp/deploy/images/superbird/{{image}}-superbird-flashthing-env-only.zip

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

# Boot the Superbird into our mainline kernel from stock u-boot.
boot-kernel:
  scripts/superbird-boot-kernel.sh

# Drop a running device back into USB burn mode for the next boot.
reboot-to-burn:
  scripts/superbird-reboot-to-burn

# Push a webapp bundle into /var/bridgething/webapps/<name>/.
push-webapp local name="":
  scripts/bridgething-push-webapp {{local}} {{name}}

# SSH-tunnel chromium's CDP from the device's 127.0.0.1:9222 to the host.
cdp port="9223":
  scripts/bridgething-cdp {{port}}

# Delta-OTA from a booted device.
ota *args:
  scripts/bridgething-ota {{args}}
