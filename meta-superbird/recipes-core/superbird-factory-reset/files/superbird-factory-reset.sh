#!/bin/sh
set -u

DEV=${SUPERBIRD_FACTORY_RESET_DEV:-/dev/disk/by-partlabel/data}
FLAG=factory_reset

armed=$(fw_printenv -n "$FLAG" 2>/dev/null) || exit 0
[ "$armed" = 1 ] || exit 0

if ! fw_setenv "$FLAG" || ! cleared=$(fw_printenv -n "$FLAG") || [ -n "$cleared" ]; then
    echo "superbird-factory-reset: could not clear $FLAG; leaving $DEV intact" >&2
    exit 1
fi

echo "superbird-factory-reset: wiping $DEV" >&2
if ! wipefs -a "$DEV"; then
    echo "superbird-factory-reset: wipefs failed on $DEV" >&2
    exit 1
fi
