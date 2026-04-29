#!/bin/sh
# Print on-disk install size for each candidate browser.
# Walks opkg's installed-package list and the file lists for each package,
# summing real disk usage (du -sk) of every file in /usr/{bin,lib,share}.

set -eu

if ! command -v opkg >/dev/null 2>&1; then
    echo "opkg not found - cannot enumerate packages" >&2
    exit 1
fi

pkgs_for() {
    case "$1" in
        cog)      echo "cog wpewebkit wpebackend-fdo libwpe1" ;;
        chromium) echo "chromium-ozone-wayland" ;;
    esac
}

size_for_pkg_set() {
    label=$1; shift
    total_b=0
    missing=0
    for p in "$@"; do
        if opkg status "$p" 2>/dev/null | grep -q "^Status: install"; then
            files=$(opkg files "$p" 2>/dev/null | tail -n +2)
            for f in $files; do
                [ -f "$f" ] || continue
                sz=$(stat -c '%s' "$f" 2>/dev/null || echo 0)
                total_b=$((total_b + sz))
            done
        else
            echo "  [missing] $p" >&2
            missing=$((missing+1))
        fi
    done
    printf "%-12s %10d B  %8.1f MiB  missing=%d\n" "$label" "$total_b" "$(awk -v b=$total_b 'BEGIN{print b/1048576}')" "$missing"
}

echo "Browser   Total size (sum of installed pkg files)"
echo "------------------------------------------------"
for label in cog chromium; do
    size_for_pkg_set "$label" $(pkgs_for "$label")
done
