#!/usr/bin/env bash
# usb gadget bring-up.
#
# Single-config composite gadget exposing two functions to the host:
#   - ncm.usb0    (uncm0)    - 10.42.1.0/24 carved into /29s per device
#                              serial. CDC-NCM has inbox drivers across
#                              Linux (cdc_ncm since 3.2), macOS, and
#                              Windows 8.1+, and aggregates Ethernet
#                              frames per USB transfer so throughput
#                              beats both RNDIS and CDC-ECM. Device
#                              side is 10.42.1.<offset+2>; host gets a
#                              DHCP lease in 10.42.1.<offset+3..6>.
#   - ffs.adb     (ep0/1/2)  - FunctionFS slot wired up at boot but driven
#                              by adbd in userspace; adbd opens
#                              /dev/usb-ffs/adb/ep0 to write the USB
#                              descriptors before the UDC is bound.
#
# Two IN endpoints carry bulk traffic (NCM, ADB) plus one interrupt-IN
# for NCM notifications. G12A's DWC2 reserves 712 dwords of SPRAM and
# the per-EP TX layout is set in the board DTS via
# &dwc2 { g-tx-fifo-size = ... } so the two bulk slots get 128 dwords
# each (one HS maxpacket).
#
# Critical: this script does NOT bind the UDC. adbd's systemd unit
# (android-tools-adbd.service with our drop-in) opens the FFS ep0 to
# write descriptors and then ExecStartPost echoes the UDC. Binding the
# UDC before adbd has written ep0 descriptors leaves the ADB function
# half-registered and the host enumeration of the gadget either hangs
# or omits ADB entirely.
#
# Runs once at boot via superbird-usb-gadget.service.
set -euo pipefail

CFG=/sys/kernel/config/usb_gadget/@@USB_GADGET_NAME@@
UDC_DIR=/sys/class/udc
FFS_MOUNT=/dev/usb-ffs/adb

if ! mountpoint -q /sys/kernel/config; then
    mount -t configfs none /sys/kernel/config
fi

# Idempotent teardown: unwind an already-composed gadget so re-runs
# during SSH iteration apply changes cleanly. The os_desc loop is a
# no-op for trees that never populated os_desc/* (the NCM-only path
# doesn't), but covers stale trees that did.
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

# Linux Foundation VID + Multifunction Composite Gadget PID.
echo 0x1d6b > "$CFG/idVendor"
echo 0x0104 > "$CFG/idProduct"
echo 0x0100 > "$CFG/bcdDevice"
echo 0x0200 > "$CFG/bcdUSB"

# Leave bDeviceClass / bDeviceSubClass / bDeviceProtocol at 0
# (per-interface). Setting the IAD class triplet (0xef/0x02/0x01) at
# device level looks correct for a multi-function composite, but the
# adb host client's libusb hotplug filter silently skips devices with
# a non-zero bDeviceClass - hosts then never see the ADB function and
# `adb devices` stays empty even though the kernel-side gadget is fine.
# CDC-NCM ships its own per-function IAD descriptor regardless, so the
# OSes that need it still get it.

mkdir -p "$CFG/strings/0x409"
echo "@@USB_MANUFACTURER@@"           > "$CFG/strings/0x409/manufacturer"
echo "@@USB_PRODUCT@@" > "$CFG/strings/0x409/product"
SERIAL=$(cat /sys/firmware/devicetree/base/serial-number 2>/dev/null | tr -d '\0' || echo "superbird0")
echo "$SERIAL"               > "$CFG/strings/0x409/serialnumber"

# Stable MACs derived from the device serial so two Superbirds on the same
# host don't collide. Locally-administered bit (0x02) set, multicast bit
# clear.
SERIAL_SHA=$(echo -n "$SERIAL" | sha256sum)
mac_suffix=$(echo "${SERIAL_SHA:0:8}" | sed 's/../&:/g; s/:$//')
NCM_HOST_MAC="02:11:44:${mac_suffix:0:8}"
NCM_DEV_MAC="02:11:55:${mac_suffix:0:8}"

# Per-serial /29 subnet so two Superbirds on the same host get distinct
# IPs. /29 = 8 addrs (network + host + device + 4-slot DHCP pool +
# broadcast). 32 disjoint subnets fit in 10.42.1.0/24.
serial_nibble=$((16#${SERIAL_SHA:0:2} & 0x1F))
subnet_offset=$((serial_nibble * 8))
NCM_DEV_IP="10.42.1.$((subnet_offset + 2))"
NCM_HOST_IP="10.42.1.$((subnet_offset + 1))"
DHCP_POOL_OFFSET=3
DHCP_POOL_SIZE=4

# CDC-NCM function. Linux, macOS, and Windows 8.1+ all bind via the
# standard CDC class triplet - no Microsoft OS descriptors needed.
mkdir -p "$CFG/functions/ncm.usb0"
echo "$NCM_HOST_MAC" > "$CFG/functions/ncm.usb0/host_addr"
echo "$NCM_DEV_MAC"  > "$CFG/functions/ncm.usb0/dev_addr"
# Kernel u_ether.c requires the ifname pattern to contain exactly one "%d"
# so the gadget framework can pick a non-colliding instance number.
echo "uncm%d"        > "$CFG/functions/ncm.usb0/ifname"

# FunctionFS slot for adbd. The kernel exposes /dev/usb-ffs/adb once we
# mount functionfs at the path; adbd opens ep0 from there to write the
# class=0xff sub=0x42 proto=0x01 ADB interface descriptor and then handles
# the bulk-in/out streams. The configfs instance suffix ("adb" in
# functions/ffs.adb) MUST match the FunctionFS mount source name in the
# `mount -t functionfs adb ...` invocation below.
mkdir -p "$CFG/functions/ffs.adb"
mkdir -p "$FFS_MOUNT"
mount -t functionfs adb "$FFS_MOUNT"

# Single config holds both functions.
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

# UDC bind is intentionally deferred to adbd's ExecStartPost. Writing the
# UDC here would expose the gadget before adbd has populated the FFS ep0
# descriptors, leaving the ADB function half-registered.
echo "gadget composed for $UDC (UDC bind deferred to adbd)"
echo "  CDC-NCM  host=$NCM_HOST_MAC dev=$NCM_DEV_MAC ifname=uncm0 subnet=$NCM_DEV_IP/29"
echo "  FFS-ADB  mounted at $FFS_MOUNT"

# Generate per-serial .network file into /run/ (priority over /etc/).
# Done after gadget bring-up so this script is the single source of
# truth for per-serial values: if the gadget script doesn't run, the
# default static .network file from /etc/ applies.
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

# Reload networkd so it picks up the per-serial .network file. Failure
# here is non-fatal - on first boot, networkd may not be running yet and
# will read /run/systemd/network/ on its own start.
if systemctl is-active --quiet systemd-networkd 2>/dev/null; then
    networkctl reload || true
fi

# Per-serial mDNS hostname so multiple devices on the same LAN don't
# collide on the bare `@@HOSTNAME@@.local`. Avahi auto-suffixes
# duplicates anyway, but a stable per-serial name lets SDK callers
# resolve a specific device without guessing. Transient (writes to
# /proc/sys/kernel/hostname) so the /etc filesystem can stay readonly.
SHORT_SERIAL=$(echo "${SERIAL_SHA:0:6}")
hostnamectl --transient set-hostname "@@HOSTNAME@@-$SHORT_SERIAL" 2>/dev/null || true

# Reload avahi so it republishes services under the new hostname.
# Non-fatal: avahi may not be running yet on first boot.
if systemctl is-active --quiet avahi-daemon 2>/dev/null; then
    systemctl reload avahi-daemon || true
fi
