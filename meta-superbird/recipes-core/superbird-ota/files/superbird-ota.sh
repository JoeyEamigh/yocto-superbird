#!/bin/sh
# Apply a mainline-layout .swu to the INACTIVE A/B slot. swupdate's sw-description
# bootenv block flips slot_active + resets that slot's tries atomically on success;
# this wrapper only picks which slot to target and drives the install over the
# swupdate.service IPC socket (swupdate-client), matching the daemon's apply path.
set -eu

swu="${1:?usage: superbird-ota <file.swu>}"
[ -f "$swu" ] || { echo "superbird-ota: file not found: $swu" >&2; exit 2; }

active="$(fw_printenv -n slot_active 2>/dev/null || echo a)"
case "$active" in
    a) target=b ;;
    b) target=a ;;
    *) target=b ;;
esac

echo "superbird-ota: active slot=$active -> writing inactive slot=$target"
exec swupdate-client -e "stable,slot_${target}" "$swu"
