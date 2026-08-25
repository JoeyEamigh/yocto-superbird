#!/usr/bin/env bash
set -euo pipefail

CFG=/sys/kernel/config/usb_gadget/@@USB_GADGET_NAME@@
UDC_DIR=/sys/class/udc
FFS_MOUNT=/dev/usb-ffs/adb

if ! mountpoint -q /sys/kernel/config; then
    mount -t configfs none /sys/kernel/config
fi

if [[ -d "$CFG" ]]; then
    echo "" > "$CFG/UDC" 2>/dev/null || true
    if mountpoint -q "$FFS_MOUNT"; then
        umount "$FFS_MOUNT" || true
    fi
    for link in "$CFG"/configs/*/*; do
        [[ -L "$link" ]] && rm -f "$link"
    done
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

echo 0x1d6b > "$CFG/idVendor"
echo 0x0104 > "$CFG/idProduct"
echo 0x0100 > "$CFG/bcdDevice"
echo 0x0200 > "$CFG/bcdUSB"

mkdir -p "$CFG/strings/0x409"
echo "@@USB_MANUFACTURER@@"           > "$CFG/strings/0x409/manufacturer"
echo "@@USB_PRODUCT@@" > "$CFG/strings/0x409/product"
SERIAL=$(cat /sys/firmware/devicetree/base/serial-number 2>/dev/null | tr -d '\0' || echo "superbird0")
echo "$SERIAL"               > "$CFG/strings/0x409/serialnumber"

SERIAL_SHA=$(echo -n "$SERIAL" | sha256sum)
mac_suffix=$(echo "${SERIAL_SHA:0:8}" | sed 's/../&:/g; s/:$//')
NCM_HOST_MAC="02:11:44:${mac_suffix:0:8}"
NCM_DEV_MAC="02:11:55:${mac_suffix:0:8}"

serial_nibble=$((16#${SERIAL_SHA:0:2} & 0x1F))
subnet_offset=$((serial_nibble * 8))
NCM_DEV_IP="10.42.1.$((subnet_offset + 2))"
NCM_HOST_IP="10.42.1.$((subnet_offset + 1))"
DHCP_POOL_OFFSET=3
DHCP_POOL_SIZE=4

mkdir -p "$CFG/functions/ncm.usb0"
echo "$NCM_HOST_MAC" > "$CFG/functions/ncm.usb0/host_addr"
echo "$NCM_DEV_MAC"  > "$CFG/functions/ncm.usb0/dev_addr"
echo "uncm%d"        > "$CFG/functions/ncm.usb0/ifname"

mkdir -p "$CFG/functions/ffs.adb"
mkdir -p "$FFS_MOUNT"
mount -t functionfs adb "$FFS_MOUNT"

mkdir -p "$CFG/configs/c.1/strings/0x409"
echo "@@USB_MANUFACTURER@@" > "$CFG/configs/c.1/strings/0x409/configuration"
echo 250           > "$CFG/configs/c.1/MaxPower"
ln -sf "$CFG/functions/ncm.usb0" "$CFG/configs/c.1/"
ln -sf "$CFG/functions/ffs.adb"  "$CFG/configs/c.1/"

UDC=$(ls "$UDC_DIR" | head -n 1)
if [[ -z "$UDC" ]]; then
    echo "no UDC available under $UDC_DIR; is dwc2 probed?" >&2
    exit 1
fi

echo "gadget composed for $UDC (UDC bind deferred to adbd)"
echo "  CDC-NCM  host=$NCM_HOST_MAC dev=$NCM_DEV_MAC ifname=uncm0 subnet=$NCM_DEV_IP/29"
echo "  FFS-ADB  mounted at $FFS_MOUNT"

mkdir -p /run/systemd/network

cat > /run/systemd/network/11-usb-ncm.network <<NETWORK_NCM
[Match]
Name=uncm*

[Network]
Address=$NCM_DEV_IP/29
DHCPServer=yes
LinkLocalAddressing=no
IPv6AcceptRA=no
IPMasquerade=no
ConfigureWithoutCarrier=yes
EmitLLDP=no

[DHCPServer]
PoolOffset=$DHCP_POOL_OFFSET
PoolSize=$DHCP_POOL_SIZE
EmitDNS=no
EmitNTP=no
EmitRouter=no

[Link]
RequiredForOnline=no
NETWORK_NCM

if systemctl is-active --quiet systemd-networkd 2>/dev/null; then
    networkctl reload || true
fi

ALIAS_SUFFIX=$(echo -n "$SERIAL" | tr -cd '[:alnum:]' | tr '[:upper:]' '[:lower:]')
if [[ ${#ALIAS_SUFFIX} -gt 4 ]]; then
    ALIAS_SUFFIX=${ALIAS_SUFFIX: -4}
fi
if [[ -n "$ALIAS_SUFFIX" ]]; then
    cat > /run/superbird-mdns-alias.env <<ALIAS_ENV
ALIAS=@@HOSTNAME@@-$ALIAS_SUFFIX.local
ADDR=$NCM_DEV_IP
ALIAS_ENV
    echo "  mDNS     alias=@@HOSTNAME@@-$ALIAS_SUFFIX.local addr=$NCM_DEV_IP"
fi
