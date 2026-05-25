#!/bin/sh
# reset the active slot's u-boot try counter via fw_setenv. slot from superbird.slot= on cmdline.
set -eu

SLOT=""
for arg in $(cat /proc/cmdline); do
    case "$arg" in
        superbird.slot=*) SLOT="${arg#superbird.slot=}" ;;
    esac
done

case "$SLOT" in
    a|b) ;;
    *)
        echo "superbird-slot-ok: no/invalid superbird.slot ('$SLOT') - nothing to do"
        exit 0
        ;;
esac

if ! command -v fw_setenv >/dev/null 2>&1; then
    echo "superbird-slot-ok: fw_setenv missing - cannot reset counter" >&2
    exit 1
fi

fw_setenv "slot_${SLOT}_tries" 3
echo "superbird-slot-ok: slot $SLOT healthy, reset slot_${SLOT}_tries=3"
