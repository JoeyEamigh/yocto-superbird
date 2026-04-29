#!/bin/sh
# Drive a single benchmark for one browser. Captures install size,
# launches via systemd-run + wsh, monitors metrics until the unit
# stops, then prints a side-by-side row.
#
# Usage:
#   run-bench.sh cog       speedometer3
#   run-bench.sh chromium  motionmark
#   run-bench.sh cog       custom -- https://...

set -eu

BROWSER=$1; shift
TEST=$1; shift

case "$TEST" in
    speedometer3)  URL="https://browserbench.org/Speedometer3.1/" ;;
    motionmark)    URL="https://browserbench.org/MotionMark1.3.1/" ;;
    jetstream)     URL="https://browserbench.org/JetStream/" ;;
    blank)         URL="about:blank" ;;
    custom)
        [ "${1:-}" = "--" ] || { echo "custom needs -- <url>" >&2; exit 2; }
        shift; URL=$1; shift ;;
    *) echo "unknown test: $TEST (speedometer3|motionmark|jetstream|blank|custom)" >&2; exit 2 ;;
esac

case "$BROWSER" in
    cog)
        CMD="cog -P wl --window-fullscreen"
        PKGS="cog wpewebkit wpebackend-fdo libwpe1"
        ;;
    chromium)
        CMD="chromium --window-size=480,800 --window-position=0,0 --no-sandbox"
        PKGS="chromium-ozone-wayland"
        ;;
    *) echo "unknown browser: $BROWSER (cog|chromium)" >&2; exit 2 ;;
esac

LABEL="$BROWSER-$TEST"
echo "==> browser=$BROWSER test=$TEST url=$URL"

echo "==> on-disk size:"
total=0
missing=0
for p in $PKGS; do
    if opkg status "$p" 2>/dev/null | grep -q "^Status: install"; then
        sz=$(opkg files "$p" 2>/dev/null | tail -n +2 | xargs -I{} stat -c '%s' "{}" 2>/dev/null | awk '{s+=$1} END {print s+0}')
        total=$((total + sz))
        printf "  %-25s %8d B\n" "$p" "$sz"
    else
        printf "  %-25s [missing]\n" "$p"
        missing=$((missing+1))
    fi
done
printf "  %-25s %8d B  (%.1f MiB)\n" "TOTAL" "$total" "$(awk -v b=$total 'BEGIN{print b/1048576}')"

[ "$missing" -gt 0 ] && { echo "==> abort: $missing pkgs missing" >&2; exit 1; }

DIR=$(dirname "$0")
exec "$DIR/browser-bench.sh" "$LABEL" --launch -- $CMD "$URL"
