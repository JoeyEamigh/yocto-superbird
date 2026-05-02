#!/usr/bin/env bash
# Bridgething USB gadget bring-up.
#
# Multi-config layout (mirrors kernel g_multi):
#   - c.1 = RNDIS (host_if name "usb_rndis"), tagged with Microsoft OS 1.0
#     compat IDs so Windows picks this config and loads its inbox RNDIS
#     driver without any .inf install.
#   - c.2 = CDC-ECM (host_if name "usb_ecm"). macOS and most Linux distros
#     either prefer this config outright or fall back to it when the host
#     has no RNDIS support; it is also the inbox driver path on Windows
#     10 1809+.
#
# Only one configuration is bound on the host at a time, so exactly one of
# usb_rndis / usb_ecm gets carrier. systemd-networkd ships a .network file
# for each interface (see 10-usb-rndis.network / 11-usb-ecm.network) and
# runs an internal DHCP server handing 10.42.1.1 to whichever interface is
# active so the host gets a routable address with zero per-OS setup. Avahi
# advertises bridgething.local on the same interfaces.
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
    # OS desc may symlink a config; remove it before tearing configs down.
    for link in "$CFG"/os_desc/*; do
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
# bcdUSB 0x0200 keeps wide host compatibility; OS 1.0 descriptors don't need
# 2.1, and BOS/OS 2.0 add no value here (RNDIS inbox driver works via OS 1.0).
echo 0x0200 > "$CFG/bcdUSB"

mkdir -p "$CFG/strings/0x409"
echo "bridgething"           > "$CFG/strings/0x409/manufacturer"
echo "Bridgething Superbird" > "$CFG/strings/0x409/product"
SERIAL=$(cat /sys/firmware/devicetree/base/serial-number 2>/dev/null | tr -d '\0' || echo "superbird0")
echo "$SERIAL"               > "$CFG/strings/0x409/serialnumber"

# Stable MACs derived from the device serial so two Superbirds on the same
# host don't collide. Locally-administered bit (0x02) set, multicast bit
# clear. Each function gets its own (host_addr, dev_addr) pair so the host
# sees distinct MACs across RNDIS and ECM (lets NetworkManager scope
# profiles per-function if a host ever ends up with both visible).
mac_suffix=$(echo -n "$SERIAL" | sha256sum | cut -c1-8 | sed 's/../&:/g; s/:$//')
RNDIS_HOST_MAC="02:11:22:${mac_suffix:0:8}"
RNDIS_DEV_MAC="02:11:33:${mac_suffix:0:8}"
ECM_HOST_MAC="02:11:44:${mac_suffix:0:8}"
ECM_DEV_MAC="02:11:55:${mac_suffix:0:8}"

# Microsoft OS 1.0 descriptors. Windows queries the device with the
# vendor-specific request 0xCD; the gadget framework intercepts that and
# returns the per-function compatible-id table below. The string "MSFT100"
# (UTF-16LE, 7 chars + a one-byte vendor code) is what Windows looks up
# via a magic string descriptor at index 0xEE - configfs handles that for
# us once `use=1` is set.
echo 1       > "$CFG/os_desc/use"
echo 0xcd    > "$CFG/os_desc/b_vendor_code"
echo MSFT100 > "$CFG/os_desc/qw_sign"

# RNDIS function. The compatible-id "RNDIS" + sub-compatible "5162001"
# triggers Windows' inbox RNDIS-over-USB driver, no .inf required.
mkdir -p "$CFG/functions/rndis.usb0"
echo "$RNDIS_HOST_MAC" > "$CFG/functions/rndis.usb0/host_addr"
echo "$RNDIS_DEV_MAC"  > "$CFG/functions/rndis.usb0/dev_addr"
# Kernel u_ether.c requires the ifname pattern to contain exactly one "%d"
# so the gadget framework can pick a non-colliding instance number.
echo "urndis%d"        > "$CFG/functions/rndis.usb0/ifname"
echo "RNDIS"           > "$CFG/functions/rndis.usb0/os_desc/interface.rndis/compatible_id"
echo "5162001"         > "$CFG/functions/rndis.usb0/os_desc/interface.rndis/sub_compatible_id"

# CDC-ECM function. No OS desc needed - Linux/macOS recognise it via the
# standard CDC class triplet, and Windows 10 1809+ ships an inbox driver
# that auto-installs the same way.
mkdir -p "$CFG/functions/ecm.usb0"
echo "$ECM_HOST_MAC" > "$CFG/functions/ecm.usb0/host_addr"
echo "$ECM_DEV_MAC"  > "$CFG/functions/ecm.usb0/dev_addr"
echo "uecm%d"        > "$CFG/functions/ecm.usb0/ifname"

# Config 1: RNDIS. Windows always selects the first config by default and
# the OS-desc symlink below pins this association explicitly.
mkdir -p "$CFG/configs/c.1/strings/0x409"
echo "RNDIS" > "$CFG/configs/c.1/strings/0x409/configuration"
echo 250     > "$CFG/configs/c.1/MaxPower"
ln -sf "$CFG/functions/rndis.usb0" "$CFG/configs/c.1/"
ln -sf "$CFG/configs/c.1" "$CFG/os_desc/c.1"

# Config 2: CDC-ECM. macOS and Linux without rndis_host fall back to this.
mkdir -p "$CFG/configs/c.2/strings/0x409"
echo "CDC-ECM" > "$CFG/configs/c.2/strings/0x409/configuration"
echo 250       > "$CFG/configs/c.2/MaxPower"
ln -sf "$CFG/functions/ecm.usb0" "$CFG/configs/c.2/"

UDC=$(ls "$UDC_DIR" | head -n 1)
if [[ -z "$UDC" ]]; then
    echo "no UDC available under $UDC_DIR; is dwc2 probed?" >&2
    exit 1
fi
echo "$UDC" > "$CFG/UDC"

echo "gadget bound to $UDC"
echo "  RNDIS   c.1  host=$RNDIS_HOST_MAC dev=$RNDIS_DEV_MAC ifname=urndis0"
echo "  CDC-ECM c.2  host=$ECM_HOST_MAC  dev=$ECM_DEV_MAC  ifname=uecm0"
