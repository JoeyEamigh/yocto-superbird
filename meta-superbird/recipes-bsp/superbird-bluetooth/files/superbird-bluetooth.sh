#!/bin/sh
# Bring up the Superbird's BCM20703A2 over UART_A (= /dev/ttyAML1) at 4 Mbps.
#
# REG_ON (GPIOX_17) is pulsed LOW->HIGH to take the chip out of hardware reset;
# userspace btattach + the broadcom protocol carry HCI (mainline hci_bcm serdev
# hits a Reset->ReadLocalVersion timeout we sidestep this way).
#
# The service must be FAST and BOUNCE-FREE: under first-boot load the chip comes
# up but btattach can exit shortly after the baud bump. The old code ended in
# `wait $BTATTACH_PID`, so btattach's exit became the service's exit -> a spurious
# restart even though BT was already UP@4M. Instead we bring up to UP@4M, signal
# readiness ONCE, then SUPERVISE btattach: if it dies we re-bring-up in-script
# within a budget and never exit non-zero after readiness.

set -u

GPIO_CHIP=0
REG_ON_LINE=82            # GPIOX_17, chip enable / REG_ON
DEV_WAKE_LINE=72          # GPIOX_7, BT_DEV_WAKE - keeps the chip out of deep sleep
TTY="/dev/ttyAML1"
LOW_HOLD_MS=100           # matches nixos-superbird stock 4.9 timing
HIGH_SETTLE_MS=300

# Highest rate this board's BCM<->SoC H4 UART carries cleanly under sustained load.
# H4 carries no CRC, so UART bit errors do not surface as UART/HCI errors; they reach
# iAP2 as link-checksum failures and trigger retransmits, so the only symptom is severe
# slowness on large transfers. 4M is an exact divisor and looks clean but silently flips
# ~1 byte per 38 KB under load (fine for tiny frames, ~35% loss on 16 KB ones). Do NOT
# raise without moving to a CRC-protected transport (H5). 2M (~200 KB/s) still clears the
# 3-DH5 EDR radio's ~125 KB/s ceiling, so it does not cap throughput. 115200 caps every BT
# transfer near ~11 KB/s (single album art ~22s), so do not lower either. (3M does not
# resync via the FC18 + stty path - the meson divisor rounds and the chip desyncs.)
TARGET_BAUD=2000000

# OEM-stamped public BDADDR cell. iAP2 IdentificationInformation carries this MAC
# and the iPhone rejects identification if it does not match the bonded address.
BT_MAC_CELL_GLOB="/sys/bus/nvmem/devices/efuse0/cells/bt-mac@*"

# Wall-clock budget to first UP@4M. The .service TimeoutStartSec must be larger so
# systemd doesn't kill the start before we exhaust this.
BRINGUP_BUDGET_S=40
# Per-attempt cap on the synchronous hciconfig-up patchram window before we give
# up on this attempt and re-pulse. Healthy is ~6s; a struggling open is longer.
UP_WAIT_S=14

BTATTACH_PID=""

log() { printf '[superbird-bluetooth] %s\n' "$*"; }
now() { date +%s; }

line_state() { grep -E "^ gpio-$1 " /sys/kernel/debug/gpio 2>/dev/null; }
reg_is_high() { line_state "$REG_ON_LINE" | grep -q 'hi'; }

# Kill every gpioset/btattach/hciconfig so a fresh pulse can re-acquire the lines
# and no stray background `hciconfig up` from a fast-abandoned attempt lingers in
# the kernel. libgpiod refuses a second request on a held line, and selectively
# matching a holder by cmdline is fragile, so we release everything and
# re-establish from scratch.
kill_all() {
    for pid in $(pidof gpioset 2>/dev/null); do kill -9 "${pid}" 2>/dev/null || true; done
    for pid in $(pidof btattach 2>/dev/null); do kill -9 "${pid}" 2>/dev/null || true; done
    for pid in $(pidof hciconfig 2>/dev/null); do kill -9 "${pid}" 2>/dev/null || true; done
    BTATTACH_PID=""
}

# Poll debugfs until both BT lines are unclaimed (a released line vanishes from
# the dump) so a re-acquire won't hit "Device or resource busy".
wait_lines_released() {
    i=0
    while [ "${i}" -lt 20 ]; do
        if [ -z "$(line_state "${REG_ON_LINE}")" ] && [ -z "$(line_state "${DEV_WAKE_LINE}")" ]; then
            return 0
        fi
        sleep 0.1
        i=$(( i + 1 ))
    done
    return 1
}

# Hold a line at a value via a daemonized gpioset, retrying while the line is
# still busy (a just-killed holder's fd not yet closed by the kernel).
gpio_hold() {
    _ln="$1"; _v="$2"; _try=0
    while [ "${_try}" -lt 30 ]; do
        if gpioset -c "${GPIO_CHIP}" --daemonize "${_ln}=${_v}" 2>/dev/null; then
            return 0
        fi
        sleep 0.1
        _try=$(( _try + 1 ))
    done
    return 1
}

ms_to_s() {
    _ms="$1"
    printf '%d.%03d' "$(( _ms / 1000 ))" "$(( _ms % 1000 ))"
}

# Clean power-cycle: release all -> drive DEV_WAKE+REG_ON low -> hold -> release
# all -> drive DEV_WAKE+REG_ON high. Returns non-zero if REG_ON does not end up
# actually driven high (so the caller treats it as a failed attempt, not a wedge).
pulse_chip_enable() {
    kill_all
    wait_lines_released
    log "asserting DEV_WAKE (line ${DEV_WAKE_LINE}) LOW, REG_ON (line ${REG_ON_LINE}) LOW for ${LOW_HOLD_MS}ms"
    gpio_hold "${DEV_WAKE_LINE}" 0 || { log "pulse: DEV_WAKE acquire busy"; return 1; }
    gpio_hold "${REG_ON_LINE}" 0  || { log "pulse: REG_ON-low acquire busy"; return 1; }
    sleep "$(ms_to_s "${LOW_HOLD_MS}")"

    kill_all
    wait_lines_released || { log "pulse: lines stuck held"; return 1; }
    gpio_hold "${DEV_WAKE_LINE}" 0 || { log "pulse: DEV_WAKE reacquire busy"; return 1; }
    gpio_hold "${REG_ON_LINE}" 1  || { log "pulse: REG_ON-high acquire busy"; return 1; }
    sleep "$(ms_to_s "${HIGH_SETTLE_MS}")"

    if ! reg_is_high; then
        log "pulse: REG_ON not high after set"
        return 1
    fi
    log "REG_ON HIGH (chip powered)"
    return 0
}

# `-S 115200` pins the ldisc oper_speed to the chip's natural post-patchram baud
# so hci_bcm's set_baudrate is a no-op round-trip; we bump to 4M ourselves after.
# No `-N`: we want hardware flow control (without it meson_uart parks RTS via
# TWO_WIRE_EN and the chip never sees CTS ready).
attach_btattach() {
    if [ ! -c "${TTY}" ]; then
        log "ERROR: ${TTY} not found - check serial1 alias in DTS"
        return 1
    fi
    log "btattach -P bcm -S 115200 -B ${TTY}"
    btattach -P bcm -S 115200 -B "${TTY}" &
    BTATTACH_PID=$!
    return 0
}

btattach_alive() {
    [ -n "${BTATTACH_PID}" ] && kill -0 "${BTATTACH_PID}" 2>/dev/null
}

# Bring hci0 UP for this attempt. `hciconfig up` runs the kernel bcm_setup
# synchronously (healthy ~6s). A silent chip (browned out under first-boot eMMC
# load) instead blocks ~47s in btbcm's HCI_Reset retry chain, so we run the up in
# the background and abandon the attempt the moment the reset-wedge canary appears
# (~2s); a fresh REG_ON re-pulse recovers a wedged chip where in-place retries do
# not. The canary keys on the HCI_Reset failure only - the benign post-init -110s
# (0x0c7a/0x0c52 this firmware does not answer) must NOT trip it. Non-zero return
# makes the caller re-pulse.
wait_up() {
    i=0
    while [ "${i}" -lt 12 ]; do
        [ -e /sys/class/bluetooth/hci0 ] && break
        sleep 0.1
        i=$(( i + 1 ))
    done
    [ -e /sys/class/bluetooth/hci0 ] || { log "wait_up: hci0 never appeared"; return 1; }

    _dmark=$(dmesg 2>/dev/null | wc -l)
    hciconfig hci0 up >/dev/null 2>&1 &
    _up_pid=$!
    _deadline=$(( $(now) + UP_WAIT_S ))
    while [ "$(now)" -lt "${_deadline}" ]; do
        if hciconfig hci0 2>/dev/null | grep -qw UP; then
            return 0
        fi
        if ! btattach_alive; then
            log "wait_up: btattach exited during bring-up"
            kill "${_up_pid}" 2>/dev/null || true
            return 1
        fi
        if dmesg 2>/dev/null | sed -n "$(( _dmark + 1 )),\$p" | grep -qiE 'Reset failed|0x0c03 tx timeout'; then
            log "wait_up: chip silent (HCI_Reset wedge); abandoning attempt to re-pulse"
            kill "${_up_pid}" 2>/dev/null || true
            return 1
        fi
        sleep 0.5
    done
    log "wait_up: hci0 did not reach UP within ${UP_WAIT_S}s"
    kill "${_up_pid}" 2>/dev/null || true
    return 1
}

# Write the OEM-stamped public BDADDR and reset so it latches. Best-effort: a
# failure here does not fail the attempt (the chip still works with its patchram
# default; only iAP2 MAC cross-check cares, and bring-up correctness comes first).
program_efuse_bdaddr() {
    cell=$(ls ${BT_MAC_CELL_GLOB} 2>/dev/null | head -n 1)
    if [ -z "${cell}" ] || [ ! -r "${cell}" ]; then
        log "WARN: efuse bt-mac cell absent; keeping patchram default BDADDR"
        return 0
    fi
    mac_be=$(hexdump -e '5/1 "%02X:" 1/1 "%02X"' "${cell}")
    hex_le=$(hexdump -e '6/1 "%02X "' "${cell}" \
        | awk '{ for (i=NF; i>0; i--) printf "%s%s", $i, (i==1 ? "" : " ") }')
    log "writing efused BDADDR ${mac_be} (FC01)"
    if ! hcitool -i hci0 cmd 0x3F 0x001 ${hex_le} >/dev/null 2>&1; then
        log "WARN: HCI_BCM_Write_BDADDR failed; keeping current BDADDR"
        return 0
    fi
    hciconfig hci0 reset >/dev/null 2>&1 || log "WARN: reset after BDADDR write failed"
    return 0
}

# Bump the chip + host UART to TARGET_BAUD. The chip retunes before emitting
# FC18's command-complete, so those bytes land at the wrong baud and desync the
# ldisc; a down/up re-syncs it at the new baud. Returns non-zero (so the attempt
# re-pulses to a clean 115200 state) if the link does not come back responsive.
bump_baud() {
    if [ "${TARGET_BAUD}" -eq 115200 ]; then
        return 0
    fi
    b0=$(( TARGET_BAUD & 0xff ))
    b1=$(( (TARGET_BAUD >> 8 ) & 0xff ))
    b2=$(( (TARGET_BAUD >> 16) & 0xff ))
    b3=$(( (TARGET_BAUD >> 24) & 0xff ))
    h0=$(printf '%02x' "${b0}"); h1=$(printf '%02x' "${b1}")
    h2=$(printf '%02x' "${b2}"); h3=$(printf '%02x' "${b3}")
    log "FC18 chip baud -> ${TARGET_BAUD}"
    if ! hcitool -i hci0 cmd 0x3F 0x018 0x00 0x00 "0x${h0}" "0x${h1}" "0x${h2}" "0x${h3}" >/dev/null 2>&1; then
        log "bump_baud: FC18 command failed"
        return 1
    fi
    sleep 0.1
    if ! stty -F "${TTY}" "${TARGET_BAUD}" cs8 -parenb -cstopb crtscts >/dev/null 2>&1; then
        log "bump_baud: stty to ${TARGET_BAUD} failed"
        return 1
    fi
    hciconfig hci0 down >/dev/null 2>&1 || true
    sleep 0.3
    i=0
    while [ "${i}" -lt 6 ]; do
        if hciconfig hci0 up >/dev/null 2>&1 && hciconfig hci0 | grep -qw UP; then
            # confirm the chip actually answers at the new baud, not just that the
            # ldisc reports UP - one HCI read must complete.
            if hcitool -i hci0 cmd 0x04 0x0001 >/dev/null 2>&1; then
                log "UP@${TARGET_BAUD} (responsive)"
                return 0
            fi
        fi
        btattach_alive || { log "bump_baud: btattach exited during resync"; return 1; }
        sleep 0.3
        i=$(( i + 1 ))
    done
    log "bump_baud: link not responsive at ${TARGET_BAUD}"
    return 1
}

# Keep the ACL link in active mode (role-switch only, no sniff/hold/park). Sniff is
# BT power-save: the link parks between bursts and pays a wake latency on each new one,
# and btmon shows it flapping constantly under load. This device is bus-powered, so the
# power saving buys nothing and the latency is pure loss. Re-applied per bring-up because
# the bump_baud down/up resets adapter link policy.
set_link_policy() {
    hciconfig hci0 lp rswitch >/dev/null 2>&1 || log "WARN: could not clear sniff from link policy"
}

# One full bring-up attempt. On success btattach is running and hci0 is UP@2M and
# responsive. On any failure returns non-zero; the next attempt's pulse kills the
# leftover btattach and starts clean.
attempt() {
    pulse_chip_enable || return 1
    attach_btattach    || return 1
    wait_up            || return 1
    program_efuse_bdaddr
    bump_baud          || return 1
    set_link_policy
    return 0
}

bringup_to_ready() {
    deadline=$(( $(now) + BRINGUP_BUDGET_S ))
    n=0
    while [ "$(now)" -lt "${deadline}" ]; do
        n=$(( n + 1 ))
        log "bring-up attempt ${n}"
        if attempt; then
            log "UP@${TARGET_BAUD} on attempt ${n}"
            return 0
        fi
        log "attempt ${n} failed; re-pulsing"
    done
    return 1
}

main() {
    if [ -e /sys/class/bluetooth/hci0 ]; then
        hciconfig hci0 down >/dev/null 2>&1 || true
    fi

    if ! bringup_to_ready; then
        # Could not reach UP@4M within budget. Do NOT signal readiness (readiness
        # means UP@4M so bluetoothd opens a 4M adapter). Let systemd's
        # TimeoutStartSec restart us for a fresh try; a genuinely dead chip is the
        # daemon's "assume adapter dead" backstop, not ours to mask.
        log "ERROR: no UP@${TARGET_BAUD} within ${BRINGUP_BUDGET_S}s budget"
        kill_all
        exit 1
    fi

    if [ -n "${NOTIFY_SOCKET:-}" ]; then
        systemd-notify --ready
    fi

    # Supervise: btattach must stay attached to hold the ldisc. If it exits
    # (observed ~1/3 of first boots shortly after the baud bump), re-bring-up
    # in-script rather than letting the service fail and bounce. Never exit
    # non-zero after readiness.
    while :; do
        if btattach_alive; then
            wait "${BTATTACH_PID}"
            ec=$?
        else
            ec="gone"
        fi
        log "btattach exited (status ${ec}); re-bringing-up to hold the link"
        if ! bringup_to_ready; then
            log "WARN: re-bring-up did not reach UP@${TARGET_BAUD}; retrying"
        fi
    done
}

main "$@"
