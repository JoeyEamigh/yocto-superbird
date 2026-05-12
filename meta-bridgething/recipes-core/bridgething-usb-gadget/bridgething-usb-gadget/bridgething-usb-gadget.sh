#!/usr/bin/env bash
# Bridgething USB gadget bring-up.
#
# Single-config composite gadget exposing three functions to the host:
#   - rndis.usb0  (urndis0)  - 10.42.2.0/24 subnet. Microsoft OS 1.0
#                              compat-id triggers Windows' inbox RNDIS
#                              driver, no .inf needed. Device side is
#                              10.42.2.2; host gets a DHCP lease in
#                              10.42.2.10-17.
#   - ecm.usb0    (uecm0)    - 10.42.1.0/24 subnet. Linux/macOS pick
#                              this via the standard CDC class triplet;
#                              Windows 10 1809+ inbox CDC-ECM also works.
#                              Device side is 10.42.1.2; host gets a
#                              DHCP lease in 10.42.1.10-17.
#   - ffs.adb     (ep0/1/2)  - FunctionFS slot wired up at boot but driven
#                              by adbd in userspace; adbd writes the USB
#                              descriptors to /dev/usb-ffs/adb/ep0 before
#                              the UDC is bound.
#
# Each network function lives on its own /24 - no bridge - so Linux's
# kernel routes 10.42.1.2 unambiguously through ECM (faster + lower
# RNDIS protocol overhead) and Windows reaches 10.42.2.2 through RNDIS.
# Each interface runs systemd-networkd's internal DHCP server. Avahi
# publishes bridgething.local on both, so per-OS hostname resolution
# returns whichever subnet the host's interface is on.
#
# Three IN endpoints carry bulk traffic (RNDIS, ECM, ADB) plus two
# interrupt-IN for RNDIS / ECM notifications - 5 dedicated TX FIFOs total.
# G12A's DWC2 reserves 712 dwords of SPRAM; the per-EP TX layout is set
# in the board DTS via &dwc2 { g-tx-fifo-size = ... } so the three bulk
# slots get 128 dwords each (one HS maxpacket).
#
# Critical: this script does NOT bind the UDC. adbd's systemd unit
# (android-tools-adbd.service with our drop-in) opens the FFS ep0 to
# write descriptors and then ExecStartPost echoes the UDC. Binding the
# UDC before adbd has written ep0 descriptors leaves the ADB function
# half-registered and the host enumeration of the gadget either hangs
# or omits ADB entirely.
#
# Runs once at boot via bridgething-usb-gadget.service.
set -euo pipefail

CFG=/sys/kernel/config/usb_gadget/bridgething
UDC_DIR=/sys/class/udc
FFS_MOUNT=/dev/usb-ffs/adb

if ! mountpoint -q /sys/kernel/config; then
    mount -t configfs none /sys/kernel/config
fi

# Idempotent teardown: if the gadget already exists, unwind it so re-run
# applies changes cleanly (useful during script iteration via SSH).
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
# RNDIS and CDC-ECM each ship their own per-function IAD descriptor
# regardless, so the OSes that need it still get it.

mkdir -p "$CFG/strings/0x409"
echo "bridgething"           > "$CFG/strings/0x409/manufacturer"
echo "Bridgething Superbird" > "$CFG/strings/0x409/product"
SERIAL=$(cat /sys/firmware/devicetree/base/serial-number 2>/dev/null | tr -d '\0' || echo "superbird0")
echo "$SERIAL"               > "$CFG/strings/0x409/serialnumber"

# Stable MACs derived from the device serial so two Superbirds on the same
# host don't collide. Locally-administered bit (0x02) set, multicast bit
# clear. Each network function gets its own (host_addr, dev_addr) pair.
SERIAL_SHA=$(echo -n "$SERIAL" | sha256sum)
mac_suffix=$(echo "${SERIAL_SHA:0:8}" | sed 's/../&:/g; s/:$//')
RNDIS_HOST_MAC="02:11:22:${mac_suffix:0:8}"
RNDIS_DEV_MAC="02:11:33:${mac_suffix:0:8}"
ECM_HOST_MAC="02:11:44:${mac_suffix:0:8}"
ECM_DEV_MAC="02:11:55:${mac_suffix:0:8}"

# Per-serial subnets so two Superbirds on the same host get distinct IPs.
# Without this, both devices try to bind 10.42.1.2 on their ECM interface
# and the host's routing table only sees one. Subnets are /29 (8 addrs
# each = device + host + 4-slot DHCP pool + network/broadcast), packed
# into the second octet so ECM lives in 10.42.0.0/24 and RNDIS in
# 10.42.1.0/24 with up to 32 distinct devices per network type.
serial_nibble=$((16#${SERIAL_SHA:0:2} & 0x1F))
subnet_offset=$((serial_nibble * 8))
ECM_DEV_IP="10.42.0.$((subnet_offset + 2))"
ECM_HOST_IP="10.42.0.$((subnet_offset + 1))"
RNDIS_DEV_IP="10.42.1.$((subnet_offset + 2))"
RNDIS_HOST_IP="10.42.1.$((subnet_offset + 1))"
DHCP_POOL_OFFSET=3
DHCP_POOL_SIZE=4

# Microsoft OS 1.0 descriptors. Windows queries the device with vendor
# request 0xCD; the gadget framework intercepts that and returns the
# per-function compatible-id table. The "MSFT100" string descriptor at
# index 0xEE is auto-generated once `use=1` is set.
echo 1       > "$CFG/os_desc/use"
echo 0xcd    > "$CFG/os_desc/b_vendor_code"
echo MSFT100 > "$CFG/os_desc/qw_sign"

# RNDIS function. Compat-id "RNDIS" + sub-compat "5162001" triggers
# Windows' inbox RNDIS-over-USB driver, no .inf required.
mkdir -p "$CFG/functions/rndis.usb0"
echo "$RNDIS_HOST_MAC" > "$CFG/functions/rndis.usb0/host_addr"
echo "$RNDIS_DEV_MAC"  > "$CFG/functions/rndis.usb0/dev_addr"
# Kernel u_ether.c requires the ifname pattern to contain exactly one "%d"
# so the gadget framework can pick a non-colliding instance number.
echo "urndis%d"        > "$CFG/functions/rndis.usb0/ifname"
echo "RNDIS"           > "$CFG/functions/rndis.usb0/os_desc/interface.rndis/compatible_id"
echo "5162001"         > "$CFG/functions/rndis.usb0/os_desc/interface.rndis/sub_compatible_id"

# CDC-ECM function. No OS desc needed - Linux/macOS recognise it via the
# standard CDC class triplet, and Windows 10 1809+ ships an inbox driver.
mkdir -p "$CFG/functions/ecm.usb0"
echo "$ECM_HOST_MAC" > "$CFG/functions/ecm.usb0/host_addr"
echo "$ECM_DEV_MAC"  > "$CFG/functions/ecm.usb0/dev_addr"
echo "uecm%d"        > "$CFG/functions/ecm.usb0/ifname"

# FunctionFS slot for adbd. The kernel exposes /dev/usb-ffs/adb once we
# mount functionfs at the path; adbd opens ep0 from there to write the
# class=0xff sub=0x42 proto=0x01 ADB interface descriptor and then handles
# the bulk-in/out streams. The configfs instance suffix ("adb" in
# functions/ffs.adb) MUST match the FunctionFS mount source name in the
# `mount -t functionfs adb ...` invocation below.
mkdir -p "$CFG/functions/ffs.adb"
mkdir -p "$FFS_MOUNT"
mount -t functionfs adb "$FFS_MOUNT"

# Single config holds all three functions. Composite layout means Linux
# sees both network interfaces simultaneously - usb-br0 on the device
# bridges traffic so reaching 10.42.1.2 works through either one.
mkdir -p "$CFG/configs/c.1/strings/0x409"
echo "Bridgething" > "$CFG/configs/c.1/strings/0x409/configuration"
echo 250           > "$CFG/configs/c.1/MaxPower"
ln -sf "$CFG/functions/rndis.usb0" "$CFG/configs/c.1/"
ln -sf "$CFG/functions/ecm.usb0"   "$CFG/configs/c.1/"
ln -sf "$CFG/functions/ffs.adb"    "$CFG/configs/c.1/"
ln -sf "$CFG/configs/c.1"          "$CFG/os_desc/c.1"

UDC=$(ls "$UDC_DIR" | head -n 1)
if [[ -z "$UDC" ]]; then
    echo "no UDC available under $UDC_DIR; is dwc2 probed?" >&2
    exit 1
fi

# UDC bind is intentionally deferred to adbd's ExecStartPost. Writing the
# UDC here would expose the gadget before adbd has populated the FFS ep0
# descriptors, leaving the ADB function half-registered.
echo "gadget composed for $UDC (UDC bind deferred to adbd)"
echo "  RNDIS    host=$RNDIS_HOST_MAC dev=$RNDIS_DEV_MAC ifname=urndis0 subnet=$RNDIS_DEV_IP/29"
echo "  CDC-ECM  host=$ECM_HOST_MAC  dev=$ECM_DEV_MAC  ifname=uecm0  subnet=$ECM_DEV_IP/29"
echo "  FFS-ADB  mounted at $FFS_MOUNT"

# Generate per-serial .network files into /run/ (priority over /etc/).
# Done after gadget bring-up so this script is the single source of
# truth for per-serial values: if the gadget script doesn't run, the
# default static .network files from /etc/ apply.
mkdir -p /run/systemd/network

cat > /run/systemd/network/11-usb-rndis.network <<NETWORK_RNDIS
[Match]
Name=urndis*

[Network]
Address=$RNDIS_DEV_IP/29
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
NETWORK_RNDIS

cat > /run/systemd/network/12-usb-ecm.network <<NETWORK_ECM
[Match]
Name=uecm*

[Network]
Address=$ECM_DEV_IP/29
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
NETWORK_ECM

# Reload networkd so it picks up the per-serial .network files. Failure
# here is non-fatal - on first boot, networkd may not be running yet and
# will read /run/systemd/network/ on its own start.
if systemctl is-active --quiet systemd-networkd 2>/dev/null; then
    networkctl reload || true
fi

# Per-serial mDNS hostname so multiple devices on the same LAN don't
# collide on the bare `bridgething.local`. Avahi auto-suffixes
# duplicates anyway, but a stable per-serial name lets SDK callers
# resolve a specific device without guessing. Transient (writes to
# /proc/sys/kernel/hostname) so the /etc filesystem can stay readonly.
SHORT_SERIAL=$(echo "${SERIAL_SHA:0:6}")
hostnamectl --transient set-hostname "bridgething-$SHORT_SERIAL" 2>/dev/null || true

# Reload avahi so it republishes services under the new hostname.
# Non-fatal: avahi may not be running yet on first boot.
if systemctl is-active --quiet avahi-daemon 2>/dev/null; then
    systemctl reload avahi-daemon || true
fi
