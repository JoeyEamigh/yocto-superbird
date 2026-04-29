#!/usr/bin/env bash
# Send a command to the Superbird console via the agent's FIFO, wait for
# shell prompt to return, and print the command output (stripped of the
# agent's pre-existing log + ANSI escapes).
#
# Usage:
#   superbird-cmd.sh 'ls /'
#   superbird-cmd.sh --timeout 10 'dmesg | head'
#   superbird-cmd.sh --raw 'ls /'            # keep ANSI codes
set -euo pipefail

LOG=/tmp/superbird-console.log
IN=/tmp/superbird-console.in
TIMEOUT=5
RAW=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --timeout) TIMEOUT="$2"; shift 2 ;;
    --raw)     RAW=1; shift ;;
    --)        shift; break ;;
    -*)        echo "unknown flag: $1" >&2; exit 2 ;;
    *)         break ;;
  esac
done

CMD="${1:?usage: superbird-cmd.sh [--timeout N] [--raw] 'command'}"

TAG=$(date +%s%N)
BEGIN="__SB_BEGIN_${TAG}__"
END="__SB_END_${TAG}__"

BEFORE=$(wc -c < "$LOG")

printf 'echo %s; %s; echo %s\n' "$BEGIN" "$CMD" "$END" > "$IN"

deadline=$(( $(date +%s) + TIMEOUT ))
while ! tail -c +$((BEFORE + 1)) "$LOG" 2>/dev/null | grep -q "$END"; do
  if (( $(date +%s) >= deadline )); then
    echo "[timeout after ${TIMEOUT}s]" >&2
    break
  fi
  sleep 0.2
done

section=$(tail -c +$((BEFORE + 1)) "$LOG" 2>/dev/null \
  | awk -v b="$BEGIN" -v e="$END" '
      $0 ~ b { printing=1; next }
      $0 ~ e { printing=0 }
      printing { print }
    ')

if (( RAW )); then
  printf '%s\n' "$section"
else
  printf '%s\n' "$section" \
    | sed -E $'s/\x1b\\[[0-9;?]*[a-zA-Z]//g; s/\r$//'
fi
