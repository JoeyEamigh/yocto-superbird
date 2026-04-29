#!/bin/sh
# First-boot (and every-boot, idempotent) initialization for the
# Superbird:
#   - Materialize /var/lib/superbird/meta.json from the build-time
#     template if it doesn't exist yet.
#   - Read btMac + serialNumber from the efuse nvmem cells and
#     patch them into the JSON if they're still empty.
#   - Seed /var/lib/bluetooth/<MAC>/settings with a friendly Alias
#     so the device shows up as "Car Thing (SN: xxxx)" when
#     advertising.
#
# /etc is on the read-only system partition, so /etc/superbird is a
# symlink -> /var/lib/superbird/meta.json (lives on the settings
# partition, survives OTA + factory data wipes).

set -eu

TEMPLATE="/usr/share/superbird/meta.json.in"
META_DIR="/var/lib/superbird"
META_PATH="$META_DIR/meta.json"

# nvmem cell paths. Kernel names the files after the DTS reg, with
# the offset baked in (e.g. bt-mac@6,0). Glob to tolerate future
# DTS reshuffles.
EFUSE_CELLS="/sys/bus/nvmem/devices/efuse0/cells"

mkdir -p "$META_DIR"

if [ ! -f "$META_PATH" ]; then
    cp "$TEMPLATE" "$META_PATH"
    chmod 0644 "$META_PATH"
fi

# Resolve cell paths via glob. Matches our DTS in
# meson-g12a-superbird.dts: bt_mac at reg <0x6 0x6>, serial_number
# at reg <0x12 0xc>.
BT_MAC_FILE=$(ls "$EFUSE_CELLS"/bt-mac@* 2>/dev/null | head -n 1)
SERIAL_FILE=$(ls "$EFUSE_CELLS"/serial-number@* 2>/dev/null | head -n 1)

if [ -z "$BT_MAC_FILE" ] || [ -z "$SERIAL_FILE" ] \
        || [ ! -r "$BT_MAC_FILE" ] || [ ! -r "$SERIAL_FILE" ]; then
    echo "superbird-init: efuse nvmem cells not present, leaving meta.json unpatched" >&2
    exit 0
fi

# bt-mac cell: 6 raw bytes -> uppercase colon-separated hex.
bt_mac=$(hexdump -e '5/1 "%02X:" 1/1 "%02X"' "$BT_MAC_FILE")

# serial-number cell: 12 ASCII bytes (e.g. "8558R481Q61R").
full_serial=$(cat "$SERIAL_FILE")
serial=$(printf '%s' "$full_serial" | tail -c 4)

if [ "${#serial}" -ne 4 ] || [ "${#bt_mac}" -ne 17 ]; then
    echo "superbird-init: invalid efuse readback (serial='$full_serial' bt_mac='$bt_mac'), leaving meta.json unpatched" >&2
    exit 0
fi

# Only patch if still placeholder. Safe to re-run after manual edits.
if grep -q '"btMac": ""' "$META_PATH"; then
    sed -i "s/\"btMac\": \"\"/\"btMac\": \"$bt_mac\"/" "$META_PATH"
fi
if grep -q '"serialNumber": ""' "$META_PATH"; then
    sed -i "s/\"serialNumber\": \"\"/\"serialNumber\": \"$full_serial\"/" "$META_PATH"
fi

# Seed bluetooth alias. bluez reads this on adapter init; setting
# it here means the very first BT advertisement carries the
# branded name without bridgething needing to drive bluez itself.
bt_settings_dir="/var/lib/bluetooth/$bt_mac"
bt_settings_file="$bt_settings_dir/settings"
if [ ! -f "$bt_settings_file" ]; then
    mkdir -p "$bt_settings_dir"
    printf "[General]\nAlias=Car Thing (SN: %s)\n" "$serial" > "$bt_settings_file"
fi
