#!/usr/bin/env bash
# Boot the Superbird into our mainline kernel ONCE. Device must be in USB burn
# mode already (not stock-booted). Reverts to burn-mode default on next reset.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUPERBIRD_TOOL="${SUPERBIRD_TOOL_DIR:-}"
RESET="${SUPERBIRD_RESET_HOLD:-$SCRIPT_DIR/superbird-reset-hold.py}"

if [[ -z "$SUPERBIRD_TOOL" || ! -d "$SUPERBIRD_TOOL" ]]; then
  cat >&2 <<EOF
SUPERBIRD_TOOL_DIR not set or invalid.

Point it at a clone of bishopdynamics' superbird-tool repo:
    git clone https://github.com/bishopdynamics/superbird-tool ~/superbird-tool
    export SUPERBIRD_TOOL_DIR=~/superbird-tool

(superbird-tool is a Python script driving Amlogic's burn-mode USB
protocol; \`just boot-kernel\` shells out to it for the bulkcmd
sequence that flips want_boot=kernel and resets.)
EOF
  exit 2
fi

cd "$SUPERBIRD_TOOL"
uv run ./superbird_tool.py --bulkcmd "setenv want_boot kernel"  | tail -3
uv run ./superbird_tool.py --bulkcmd "saveenv"                  | tail -3
uv run ./superbird_tool.py --bulkcmd "reset"                    | tail -3 || true

echo "reset pulse via RTS to make sure..."
"$RESET" --pulse --duration-ms 200 2>&1 | tail -3 || true
echo "done. watch UART."
