#!/bin/sh
machine_id=$(cat /etc/machine-id)
if [ "$machine_id" != "$(/usr/libexec/superbird-machine-id)" ]; then
    echo "superbird-journal-gc: machine-id $machine_id is not efuse-derived, keeping other journals"
    exit 0
fi

for dir in /var/log/journal/*; do
    name=${dir##*/}
    [ -d "$dir" ] && [ "${#name}" -eq 32 ] && [ "$name" != "$machine_id" ] || continue
    case "$name" in
        *[!0-9a-f]*) continue ;;
    esac
    echo "superbird-journal-gc: removing $dir"
    rm -rf "$dir"
done
