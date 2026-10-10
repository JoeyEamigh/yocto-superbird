#!/bin/sh
set -- /sys/bus/nvmem/devices/efuse0/cells/serial-number@*
cell=$1
[ -r "$cell" ] || exit 1

serial=$(cat "$cell")
case "$serial" in
    "" | *[!0-9A-Za-z]*) exit 1 ;;
esac
[ "${#serial}" -eq "$(wc -c < "$cell")" ] && [ "${#serial}" -le 16 ] || exit 1

machine_id=$(printf '%s' "$serial" | hexdump -v -e '/1 "%02x"')
while [ "${#machine_id}" -lt 32 ]; do
    machine_id="${machine_id}0"
done
echo "$machine_id"
