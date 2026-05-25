#!/usr/bin/env bash
# boot the device into the kernel once from usb burn mode. burn-mode default resumes on next reset.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLASHTHING="${FLASHTHING_CLI:-flashthing-cli}"
RESET="${SUPERBIRD_RESET_HOLD:-$SCRIPT_DIR/superbird-reset-hold.py}"

if ! command -v "$FLASHTHING" >/dev/null 2>&1; then
  echo "flashthing-cli not on PATH; cargo install flashthing-cli or set FLASHTHING_CLI" >&2
  exit 2
fi

"$FLASHTHING" --bulkcmd "setenv want_boot kernel"
"$FLASHTHING" --bulkcmd "saveenv"
"$FLASHTHING" --bulkcmd "reset" || true

"$RESET" --pulse --duration-ms 200 >/dev/null 2>&1 || true
echo "boot-kernel issued. watch UART."
