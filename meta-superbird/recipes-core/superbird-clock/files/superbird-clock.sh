#!/bin/sh
# superbird-clock: monotonic-forward time guard.
#
# Two operations:
#   --restore  bump system time forward to the newest available floor.
#              Runs early at boot (Before=sysinit.target time-set.target).
#   --save     touch /var/lib/clock-mtime. Runs on shutdown (via
#              ExecStop) and every 5 minutes via a timer, so an
#              unclean shutdown loses at most one tick interval.
#
# Two floor sources, newest wins: the mtime of the last --save, and the
# image build time written to /usr/share/superbird/build-epoch at image
# assembly. The build floor covers a first boot, where provision has just
# carved /var and systemd's TIME_EPOCH fallback predates the image.

set -eu

CLOCK_FILE=/var/lib/clock-mtime
BUILD_EPOCH_FILE=/usr/share/superbird/build-epoch

log() {
    echo "superbird-clock: $*"
}

# busybox date has no -Iseconds
stamp() {
    date '+%Y-%m-%d %H:%M:%S'
}

restore() {
    floor=0
    origin=""

    if [ -f "$BUILD_EPOCH_FILE" ]; then
        build_epoch=""
        read -r build_epoch < "$BUILD_EPOCH_FILE" || true
        case "$build_epoch" in
            '' | *[!0-9]*) log "ignoring malformed $BUILD_EPOCH_FILE" ;;
            *) floor=$build_epoch; origin="image build" ;;
        esac
    fi

    if [ -f "$CLOCK_FILE" ]; then
        saved=$(stat -c %Y "$CLOCK_FILE")
        if [ "$saved" -gt "$floor" ]; then
            floor=$saved
            origin="saved clock"
        fi
    fi

    if [ "$floor" -eq 0 ]; then
        log "no floor available; relying on systemd TIME_EPOCH"
        return 0
    fi

    now=$(date +%s)
    if [ "$now" -lt "$floor" ]; then
        date -s "@$floor" >/dev/null
        log "bumped time forward $((floor - now))s to $(stamp) ($origin floor)"
    else
        log "system time $(stamp) at or past the $origin floor; no change"
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
