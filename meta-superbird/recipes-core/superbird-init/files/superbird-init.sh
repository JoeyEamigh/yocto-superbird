#!/bin/sh
# every-boot init for /etc/superbird:
#   - re-render /var/lib/superbird/meta.json from the build-time template;
#     rendering each boot keeps build-identity fields honest across OTA
#     and binary pushes without depending on settings being wiped
#   - patch in efuse-derived btMac + serialNumber
#   - seed /var/lib/bluetooth/<MAC>/settings with a friendly Alias so the
#     first BT advertisement carries the branded name
#
# /etc is on the ro rootfs, so /etc/superbird is a symlink to the
# rendered file on the writable data partition.

set -eu

TEMPLATE="/usr/share/superbird/meta.json.in"
META_DIR="/var/lib/superbird"
META_PATH="$META_DIR/meta.json"

EFUSE_CELLS="/sys/bus/nvmem/devices/efuse0/cells"

mkdir -p "$META_DIR"

cp "$TEMPLATE" "$META_PATH"
chmod 0644 "$META_PATH"

# kernel names efuse cell files after the dts reg with offset baked in
# (e.g. bt-mac@6,0). glob so future dts reshuffles don't break parsing.
BT_MAC_FILE=$(ls "$EFUSE_CELLS"/bt-mac@* 2>/dev/null | head -n 1)
SERIAL_FILE=$(ls "$EFUSE_CELLS"/serial-number@* 2>/dev/null | head -n 1)

if [ -z "$BT_MAC_FILE" ] || [ -z "$SERIAL_FILE" ] \
        || [ ! -r "$BT_MAC_FILE" ] || [ ! -r "$SERIAL_FILE" ]; then
    echo "superbird-init: efuse nvmem cells not present, leaving meta.json unpatched" >&2
    exit 0
fi

# bt-mac cell: 6 raw bytes -> uppercase colon-separated hex.
bt_mac=$(hexdump -e '5/1 "%02X:" 1/1 "%02X"' "$BT_MAC_FILE")

# serial-number cell: 12 ASCII bytes.
full_serial=$(cat "$SERIAL_FILE")
serial=$(printf '%s' "$full_serial" | tail -c 4)

if [ "${#serial}" -ne 4 ] || [ "${#bt_mac}" -ne 17 ]; then
    echo "superbird-init: invalid efuse readback (serial='$full_serial' bt_mac='$bt_mac'), leaving meta.json unpatched" >&2
    exit 0
fi

if grep -q '"btMac": ""' "$META_PATH"; then
    sed -i "s/\"btMac\": \"\"/\"btMac\": \"$bt_mac\"/" "$META_PATH"
fi
if grep -q '"serialNumber": ""' "$META_PATH"; then
    sed -i "s/\"serialNumber\": \"\"/\"serialNumber\": \"$full_serial\"/" "$META_PATH"
fi

# bluez reads alias on adapter init; setting it here means the first BT
# advertisement carries the branded name without an application driving bluez.
bt_settings_dir="/var/lib/bluetooth/$bt_mac"
bt_settings_file="$bt_settings_dir/settings"
if [ ! -f "$bt_settings_file" ]; then
    mkdir -p "$bt_settings_dir"
    printf "[General]\nAlias=Car Thing (SN: %s)\n" "$serial" > "$bt_settings_file"
fi
