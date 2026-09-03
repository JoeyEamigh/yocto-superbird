#!/bin/sh
set -u

LABEL=${1:?partlabel required}
DEV=${SUPERBIRD_FSCK_DEV:-/dev/disk/by-partlabel/$LABEL}
JOURNAL_MIB=@@JOURNAL_MIB@@
MAX_REPAIR_PASSES=3

case "$LABEL" in
    bandaid) UNREPAIRABLE=format ;;
    data)    UNREPAIRABLE=leave ;;
    *)       echo "superbird-fsck: no policy for $LABEL" >&2; exit 1 ;;
esac

if [ ! -b "$DEV" ]; then
    udevadm settle
fi

if [ ! -b "$DEV" ]; then
    echo "superbird-fsck: $DEV absent; nothing to check" >&2
    exit 0
fi

REAL_DEV=$(readlink -f "$DEV")
if grep -q "^${REAL_DEV} " /proc/self/mounts; then
    echo "superbird-fsck: ${REAL_DEV} is mounted; refusing to check a live filesystem" >&2
    exit 0
fi

format() {
    echo "superbird-fsck: reformatting $DEV" >&2
    if mkfs.ext4 -F -L "$LABEL" -m 0 -J size=${JOURNAL_MIB} "$DEV"; then
        exit 0
    fi
    echo "superbird-fsck: mkfs failed on $DEV" >&2
    exit 1
}

ensure_journal() {
    if tune2fs -l "$DEV" 2>/dev/null | grep -q has_journal; then
        return
    fi
    echo "superbird-fsck: adding a journal to $DEV" >&2
    if ! tune2fs -O has_journal -J size=${JOURNAL_MIB} "$DEV"; then
        echo "superbird-fsck: could not add a journal to $DEV; leaving it journal-less" >&2
    fi
}

unreadable() {
    if [ "$UNREPAIRABLE" = format ]; then
        format
    fi
    echo "superbird-fsck: $DEV has no readable ext4 superblock; leaving it to makefs" >&2
    exit 0
}

e2fsck -p "$DEV"
rc=$?
if [ $rc -le 2 ]; then
    ensure_journal
    exit 0
fi

echo "superbird-fsck: preen refused $DEV (e2fsck rc=$rc); forcing a full repair" >&2
pass=1
while [ $pass -le $MAX_REPAIR_PASSES ]; do
    e2fsck -f -y "$DEV"
    rc=$?
    [ $rc -ge 8 ] && unreadable
    if e2fsck -f -n "$DEV" >/dev/null 2>&1; then
        echo "superbird-fsck: repaired $DEV in $pass pass(es)" >&2
        ensure_journal
        exit 0
    fi
    pass=$((pass + 1))
done

echo "superbird-fsck: $DEV still inconsistent after $MAX_REPAIR_PASSES passes" >&2
[ "$UNREPAIRABLE" = format ] && format
echo "superbird-fsck: leaving $DEV alone; its mount will fail" >&2
exit 1
