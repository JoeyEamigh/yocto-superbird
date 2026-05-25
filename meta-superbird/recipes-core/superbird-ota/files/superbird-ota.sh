#!/bin/sh
# apply a .swu to the inactive A/B slot via swupdate-client.
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
