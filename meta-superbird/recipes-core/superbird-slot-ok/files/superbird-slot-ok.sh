#!/bin/sh
# Mark the currently-booted A/B slot healthy by resetting its u-boot retry
# counter. Triggered by superbird-slot-ok.timer ~60s after boot, so it only
# runs if the system actually stayed up that long.
#
# u-boot burns one try off the active slot before every boot (so a kernel
# that boots-but-hangs still counts down toward a rollback). This resets the
# counter once we've proven the boot is healthy, so a good slot never rolls
# back. The booted slot comes from superbird.slot= on the kernel cmdline.
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
