#!/bin/sh
# Bring up the Superbird's BCM20703A2 over UART_A (= /dev/ttyAML1).
#
# The chip's REG_ON pin is on GPIOX_17 - it must be pulsed LOW->HIGH at
# init to bring the chip out of its hardware-reset state. The mainline
# hci_bcm serdev driver does this automatically via shutdown-gpios but
# hits a Reset->ReadLocalVersion timeout we couldn't diagnose; userspace
# btattach (matching stock) sidesteps the issue.

set -eu

# Mainline meson-pinctrl exposes gpiochip lines without names, so we
# can't use `gpioset GPIOX_17=...` directly. The bank order in
# pinctrl-meson-g12a.c is GPIOZ -> GPIOH -> BOOT -> GPIOC -> GPIOA ->
# GPIOX, and the cumulative offsets put GPIOA_0 at line 49 (verified
# via gpioinfo: "Preset 1" sits there) and GPIOX_0 at line 65 (49+16).
# That makes GPIOX_17 = line 82 - same offset nixos-superbird uses
# (`gpioset 0 82=1` in their bluetooth-adapter service).
GPIO_CHIP=0
REG_ON_LINE=82            # GPIOX_17, chip enable / REG_ON
DEV_WAKE_LINE=72          # GPIOX_7, BT_DEV_WAKE - keeps chip out of deep sleep
TTY="/dev/ttyAML1"
# Match stock timing exactly: 100 ms REG_ON LOW
# hold, 300 ms HIGH settle. Total ~400 ms.
LOW_HOLD_MS=100
HIGH_SETTLE_MS=300

# Operational UART baud the chip is driven to AFTER patchram. 115200 is
# the chip's natural baud immediately after patchram load; without a
# baud bump every BT transfer is capped at ~11 KB/s (115200/10 minus
# protocol overhead). The chip's firmware build 0353 supports up to
# 3 Mbps via vendor HCI_BCM_Update_Baud_Rate (0xFC18). meson_uart's
# divisor math at uartclk=24 MHz with xtal/3 gives clean integer
# multipliers at 1/2/4 Mbps; 3 Mbps would land off by 11% at xtal/3 so
# go straight to 4 Mbps (still inside the patchram's supported range -
# the radio side becomes the bottleneck around 125 KB/s effective).
TARGET_BAUD=4000000

# /sys/bus/nvmem cell containing the OEM-stamped public BDADDR. The
# DTS reg <0x6 0x6> puts this at offset 6 in efuse0; cells/bt-mac@6,0
# is the canonical kernel-named symlink. 6 raw bytes, MSB-first.
BT_MAC_CELL_GLOB="/sys/bus/nvmem/devices/efuse0/cells/bt-mac@*"

# How long we'll wait for the kernel to expose hci0 after btattach
# triggers the BCM patchram load AND for it to leave INIT state.
# bcm_setup() runs the full patchram + baudrate dance synchronously
# in the kernel and that can take >5s on a cold boot, so we give it
# 30s of margin before giving up on programming the BDADDR.
HCI_WAIT_SECONDS=30

log() { printf '[superbird-bluetooth] %s\n' "$*"; }

# Kill any daemonized gpioset/btattach processes from a previous run
# that may still hold our lines or the tty. Aggressive - kills all
# gpiosets on the system. The only producers of `gpioset` on this
# image are this script and ad-hoc use during diags; safe to
# nuke broadly during BT bringup.
release_gpios() {
    for pid in $(pidof gpioset 2>/dev/null); do
        kill -9 "${pid}" 2>/dev/null || true
    done
    for pid in $(pidof btattach 2>/dev/null); do
        kill -9 "${pid}" 2>/dev/null || true
    done
    # Wait long enough for the kernel to actually close the file
    # descriptors and release the underlying gpiod_request - empirically
    # 0.1s isn't always enough on this device, 0.5s is reliable.
    sleep 0.5
}

# Two persistent gpioset processes:
#  1. REG_ON (GPIOX_17, line 82): pulse LOW->HIGH to power-cycle chip RAM.
#     daemonize keeps the line driven HIGH after the LOW pulse so REG_ON
#     stays asserted for the chip's lifetime.
#  2. BT_DEV_WAKE (GPIOX_7, line 72): hold LOW persistently. BCM chips
#     drop into deep sleep between HCI commands when DEV_WAKE is
#     deasserted; the chip ACKs HCI_Reset's Command_Complete then goes
#     silent for every subsequent command. Asserting DEV_WAKE LOW
#     (active-low wake) fixes that. Mainline hci_bcm.c does this via
#     gpiod_get + gpiod_set_value(true) on the device-wakeup-gpios
#     property; without serdev we own the GPIO.
ms_to_s() {
    local ms="$1" s rem
    s=$(( ms / 1000 ))
    rem=$(( ms % 1000 ))
    printf '%d.%03d' "${s}" "${rem}"
}

pulse_chip_enable() {
    # Two daemonized gpioset processes for DEV_WAKE - assert LOW and
    # leave running for the lifetime of the service.
    log "asserting DEV_WAKE (chip${GPIO_CHIP} line ${DEV_WAKE_LINE}=GPIOX_7) LOW (active-low wake)"
    gpioset -c "${GPIO_CHIP}" --daemonize "${DEV_WAKE_LINE}=0"

    # Drive REG_ON LOW to fully reset the chip - daemonize so the line
    # stays driven through the hold period. Then kill that gpioset and
    # bring REG_ON HIGH with a fresh daemonize. (--toggle <interval>
    # in libgpiod 2.x actually toggles repeatedly at the interval, not
    # "go LOW then go HIGH once" - so we don't use it here.)
    log "REG_ON LOW for ${LOW_HOLD_MS}ms (full chip reset)"
    gpioset -c "${GPIO_CHIP}" --daemonize "${REG_ON_LINE}=0"
    sleep "$(ms_to_s "${LOW_HOLD_MS}")"

    # Kill ONLY the REG_ON-holding gpiosets. Identify by reading
    # /proc/<pid>/cmdline (null-separated argv): the cmdline of the
    # REG_ON-LOW gpioset contains the literal token "82=0". DEV_WAKE
    # contains "72=0", so checking for "82=0" specifically is safe.
    # libgpiod 2.x's gpioset takes "<line> <value>" as separate args
    # (not "line=value"). Find and kill the daemonized gpioset that's
    # holding REG_ON LOW so we can re-acquire the line for HIGH.
    for pid in $(pidof gpioset 2>/dev/null); do
        if [ -r "/proc/${pid}/cmdline" ]; then
            cmd=$(tr '\0' ' ' < "/proc/${pid}/cmdline")
            case "${cmd}" in
                *" ${REG_ON_LINE} 0 "*|*" ${REG_ON_LINE}=0 "*)
                    kill -9 "${pid}" 2>/dev/null || true
                    ;;
            esac
        fi
    done

    # Wait for kernel to release the gpio line, then poll
    # /sys/kernel/debug/gpio to confirm the line is free before
    # attempting to re-acquire it.
    for i in 1 2 3 4 5 6 7 8 9 10; do
        sleep 0.2
        if ! grep -qE "^ gpio-${REG_ON_LINE} " /sys/kernel/debug/gpio 2>/dev/null; then
            log "REG_ON line released after ${i} polls"
            break
        fi
    done

    log "REG_ON HIGH (chip power-up); waiting ${HIGH_SETTLE_MS}ms for ROM init"
    gpioset -c "${GPIO_CHIP}" --daemonize "${REG_ON_LINE}=1"
    sleep "$(ms_to_s "${HIGH_SETTLE_MS}")"
}

attach_btattach() {
    if [ ! -c "${TTY}" ]; then
        log "ERROR: ${TTY} not found - check serial1 alias in DTS"
        return 1
    fi
    # `-S 115200` is load-bearing: hci_ldisc.c sets hu->oper_speed from
    # tty->termios.c_ospeed at line-discipline attach time. Pinning -S to
    # the chip's natural patchram-load baud (115200) makes bcm_setup's
    # post-patchram set_baudrate a no-op round-trip and patchram loads
    # cleanly. We bump the chip ourselves afterwards via FC18 so we
    # don't have to fight bluez/serdev on baud arbitration.
    #
    # No `-N` flag: we WANT hardware flow control. Without flow control,
    # meson-uart's set_termios sets TWO_WIRE_EN (BIT 15), the GPIOX_15 RTS
    # pin floats, and the BCM chip's CTS sees "host not ready" so the
    # chip won't TX even Reset response.
    log "btattach -P bcm -S 115200 -B ${TTY}"
    btattach -P bcm -S 115200 -B "${TTY}" &
    BTATTACH_PID=$!
}

# Send vendor HCI_BCM_Update_Baud_Rate (opcode 0xFC18) to drive the chip
# UART to TARGET_BAUD, then retune the host UART. After this the radio
# is the bottleneck, not the UART link to the BCM controller. Without
# this the chip stays at 115200 forever and every transfer caps at the
# ~11 KB/s UART ceiling.
bump_baud() {
    if [ "${TARGET_BAUD}" -eq 115200 ]; then
        return 0
    fi
    local b0 b1 b2 b3 hex0 hex1 hex2 hex3
    b0=$(( TARGET_BAUD        & 0xff ))
    b1=$(( (TARGET_BAUD >> 8 ) & 0xff ))
    b2=$(( (TARGET_BAUD >> 16) & 0xff ))
    b3=$(( (TARGET_BAUD >> 24) & 0xff ))
    printf -v hex0 '%02x' "$b0"
    printf -v hex1 '%02x' "$b1"
    printf -v hex2 '%02x' "$b2"
    printf -v hex3 '%02x' "$b3"
    log "FC18 set chip baud to ${TARGET_BAUD}"
    if ! hcitool -i hci0 cmd 0x3F 0x018 0x00 0x00 "0x${hex0}" "0x${hex1}" "0x${hex2}" "0x${hex3}" >/dev/null 2>&1; then
        log "WARN: FC18 baud bump command failed; staying at 115200"
        return 0
    fi
    # The chip retunes its UART before sending the FC18 command-complete
    # event, so by the time hcitool returns we already missed it. Give it
    # a beat to settle.
    sleep 0.1
    log "stty -> host UART at ${TARGET_BAUD}"
    if ! stty -F "${TTY}" "${TARGET_BAUD}" cs8 -parenb -cstopb crtscts >/dev/null 2>&1; then
        log "ERROR: stty failed to set host UART to ${TARGET_BAUD}; chip is at new baud, host is not - link is broken"
        return 1
    fi
    sleep 0.1
    if ! hciconfig hci0 reset >/dev/null 2>&1; then
        log "WARN: chip silent after retune to ${TARGET_BAUD}"
    fi
}

# Wait for the kernel to expose /sys/class/bluetooth/hci0 AND for the
# adapter to leave INIT and reach the UP state. hcitool/HCI commands
# error out with "Network is down" while the adapter is still INIT,
# so we explicitly bring it UP and confirm the kernel mark before
# issuing the BDADDR write.
wait_for_hci0() {
    local i
    for i in $(seq 1 $((HCI_WAIT_SECONDS * 10))); do
        if [ -e /sys/class/bluetooth/hci0 ]; then
            break
        fi
        sleep 0.1
    done
    if [ ! -e /sys/class/bluetooth/hci0 ]; then
        log "ERROR: hci0 did not appear within ${HCI_WAIT_SECONDS}s"
        return 1
    fi
    hciconfig hci0 up >/dev/null 2>&1 || true
    for i in $(seq 1 $((HCI_WAIT_SECONDS * 10))); do
        if hciconfig hci0 | grep -qw UP; then
            return 0
        fi
        hciconfig hci0 up >/dev/null 2>&1 || true
        sleep 0.1
    done
    log "ERROR: hci0 did not reach UP within ${HCI_WAIT_SECONDS}s"
    return 1
}

# Write the OEM-stamped public BDADDR into the BCM controller and
# reset so the new address takes effect. iAP2 IdentificationInformation
# (param 17, BluetoothTransportComponent) carries this MAC and the
# iPhone rejects identification if it doesn't match the address it
# bonded against. Without this step the BCM chip boots with a default
# / random address chosen by the patchram, which never matches the
# /etc/superbird btMac field that the device init recipe seeds from the same
# nvmem cell.
program_efuse_bdaddr() {
    local cell mac_be hex_le
    cell=$(ls ${BT_MAC_CELL_GLOB} 2>/dev/null | head -n 1)
    if [ -z "${cell}" ] || [ ! -r "${cell}" ]; then
        log "WARN: efuse bt-mac cell not present; controller keeps patchram default BDADDR"
        return 0
    fi
    # 6 raw bytes MSB-first in the cell (matches what hexdump prints
    # in superbird-init.sh). Print as colon-separated for the log line
    # and as space-separated little-endian for hcitool.
    mac_be=$(hexdump -e '5/1 "%02X:" 1/1 "%02X"' "${cell}")
    hex_le=$(hexdump -e '6/1 "%02X "' "${cell}" \
        | awk '{ for (i=NF; i>0; i--) printf "%s%s", $i, (i==1 ? "" : " ") }')
    log "writing efused BDADDR ${mac_be} via HCI_BCM_Write_BDADDR (0xFC01)"
    if ! hcitool -i hci0 cmd 0x3F 0x001 ${hex_le} >/dev/null; then
        log "WARN: HCI_BCM_Write_BDADDR command failed; controller keeps current BDADDR"
        return 0
    fi
    # The BCM chip latches the new BDADDR on the next HCI_Reset.
    # `hciconfig hci0 reset` issues HCI_Reset and re-reads local
    # info, which is what makes bluetoothd see the new address when
    # the bluetooth.service that follows enumerates the controller.
    if ! hciconfig hci0 reset >/dev/null; then
        log "WARN: hciconfig reset failed after BDADDR write"
    fi
}

main() {
    release_gpios
    # If hci0 already exists from a previous bringup (dev iteration,
    # service restart), force it DOWN before we power-cycle the chip.
    # Otherwise the kernel keeps its old BDADDR cache and hciconfig
    # reports UP RUNNING with stale info while the chip is still
    # mid-patchram-load.
    if [ -e /sys/class/bluetooth/hci0 ]; then
        log "hci0 already exists; bringing DOWN to clear stale state"
        hciconfig hci0 down >/dev/null 2>&1 || true
    fi
    pulse_chip_enable
    attach_btattach
    if wait_for_hci0; then
        program_efuse_bdaddr
        bump_baud
    fi
    # Tell systemd we are READY only after the BDADDR is programmed.
    # bluetooth.service is ordered After this unit (via Before=); with
    # Type=notify it waits for this signal before bluetoothd discovers
    # hci0, so bluetoothd reads the efused MAC instead of the patchram
    # default. NOTIFY_SOCKET is unset when this script runs outside
    # systemd (manual invocation, ssh debugging) - skip silently.
    if [ -n "${NOTIFY_SOCKET:-}" ]; then
        systemd-notify --ready
    fi
    wait "${BTATTACH_PID}"
}

main "$@"
