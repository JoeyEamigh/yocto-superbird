#!/bin/sh
# bridgething-clock: monotonic-forward time guard.
#
# Two operations:
#   --restore  read /var/lib/clock-mtime; if system time is behind
#              that file's mtime, bump system time forward. Runs
#              early at boot (Before=sysinit.target time-set.target).
#   --save     touch /var/lib/clock-mtime. Runs on shutdown (via
#              ExecStop) and every 5 minutes via a timer, so an
#              unclean shutdown loses at most one tick interval.
#
# /var is on the writable data partition and survives reboots; only
# `bridgething-ab factory-reset` (or amlmmc erase data on the next
# flash) resets the clock-mtime file.

set -eu

CLOCK_FILE=/var/lib/clock-mtime

log() {
    echo "bridgething-clock: $*"
}

restore() {
    if [ ! -f "$CLOCK_FILE" ]; then
        log "no $CLOCK_FILE; relying on systemd TIME_EPOCH floor"
        return 0
    fi
    saved=$(stat -c %Y "$CLOCK_FILE")
    now=$(date +%s)
    if [ "$now" -lt "$saved" ]; then
        date -s "@$saved" >/dev/null
        log "bumped time forward $((saved - now))s to $(date -Iseconds)"
    else
        log "system time $(date -Iseconds) ahead of saved $(date -d "@$saved" -Iseconds); no change"
    fi
}

save() {
    dir=$(dirname "$CLOCK_FILE")
    [ -d "$dir" ] || mkdir -p "$dir"
    touch "$CLOCK_FILE"
}

case "${1-}" in
    --restore) restore ;;
    --save)    save ;;
    *) echo "usage: $0 --restore|--save" >&2; exit 2 ;;
esac
