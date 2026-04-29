#!/usr/bin/env bash
# Bridgething USB gadget bring-up.
#
# Single-config, single-function CDC-ECM gadget on DWC2 (ff400000.usb). We
# tried a multi-config (RNDIS + ECM) variant, but Linux hosts got confused by
# the composite descriptors and cdc_ether's carrier negotiation never fired
# even though USB enumeration succeeded. Keep it simple - add RNDIS back
# later as a separate build variant if Windows host support is needed.
#
# Runs once at boot via bridgething-usb-gadget.service.
set -euo pipefail

CFG=/sys/kernel/config/usb_gadget/bridgething
UDC_DIR=/sys/class/udc

if ! mountpoint -q /sys/kernel/config; then
    mount -t configfs none /sys/kernel/config
fi

# Idempotent teardown: if the gadget already exists, unwind it so re-run
# applies changes cleanly (useful during script iteration via SSH).
if [[ -d "$CFG" ]]; then
    echo "" > "$CFG/UDC" 2>/dev/null || true
    for link in "$CFG"/configs/*/*; do
        [[ -L "$link" ]] && rm -f "$link"
    done
    for d in "$CFG"/configs/*/strings/*; do
        [[ -d "$d" ]] && rmdir "$d"
    done
    for d in "$CFG"/configs/*; do
        [[ -d "$d" ]] && rmdir "$d"
    done
    for d in "$CFG"/functions/*; do
        [[ -d "$d" ]] && rmdir "$d"
    done
    for d in "$CFG"/strings/*; do
        [[ -d "$d" ]] && rmdir "$d"
    done
    rmdir "$CFG"
fi

mkdir -p "$CFG"

# Linux Foundation VID + Multifunction Composite Gadget PID.
echo 0x1d6b > "$CFG/idVendor"
echo 0x0104 > "$CFG/idProduct"
echo 0x0100 > "$CFG/bcdDevice"
echo 0x0200 > "$CFG/bcdUSB"

mkdir -p "$CFG/strings/0x409"
echo "bridgething"           > "$CFG/strings/0x409/manufacturer"
echo "Bridgething Superbird" > "$CFG/strings/0x409/product"
SERIAL=$(cat /sys/firmware/devicetree/base/serial-number 2>/dev/null | tr -d '\0' || echo "superbird0")
echo "$SERIAL"               > "$CFG/strings/0x409/serialnumber"

# Stable MACs so NetworkManager profiles on the host can match reliably.
# Derived from the device serial so two Superbirds on the same host don't
# collide. Locally-administered bit (0x02) set, multicast bit (0x01) clear.
mac_suffix=$(echo -n "$SERIAL" | sha256sum | cut -c1-8 | sed 's/../&:/g; s/:$//')
HOST_MAC="02:11:22:${mac_suffix:0:8}"
DEV_MAC="02:11:33:${mac_suffix:0:8}"

mkdir -p "$CFG/functions/ecm.usb0"
echo "$HOST_MAC" > "$CFG/functions/ecm.usb0/host_addr"
echo "$DEV_MAC"  > "$CFG/functions/ecm.usb0/dev_addr"

mkdir -p "$CFG/configs/c.1/strings/0x409"
echo "CDC-ECM" > "$CFG/configs/c.1/strings/0x409/configuration"
echo 250       > "$CFG/configs/c.1/MaxPower"
ln -sf "$CFG/functions/ecm.usb0" "$CFG/configs/c.1/"

UDC=$(ls "$UDC_DIR" | head -n 1)
if [[ -z "$UDC" ]]; then
    echo "no UDC available under $UDC_DIR; is dwc2 probed?" >&2
    exit 1
fi
echo "$UDC" > "$CFG/UDC"

echo "gadget bound to $UDC (host MAC $HOST_MAC, device MAC $DEV_MAC)"
