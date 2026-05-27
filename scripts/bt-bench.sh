#!/usr/bin/env bash
# bt-bench: end-to-end Bluetooth throughput benchmark, host -> Car Thing
#
# Measures REAL throughput by:
#   1. starting device-side l2test -d (receiver, prints incoming rate)
#   2. sending a known number of bytes from host via l2test -s
#   3. polling device hciconfig RX bytes until it stops climbing
#   4. computing rate from total bytes / elapsed-to-quiesce time
#
# Usage: bt-bench.sh [carthing_mac]
#
# Requires the device to already be paired with this host (see bridgething's
# auto-accept pairing agent).

set -eu
MAC="${1:-30:E3:D6:03:96:1E}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SSH="${SCRIPT_DIR}/superbird-ssh"
PSM=0x1011
HCI=hci0

run_test() {
    local label="$1" frames="$2" size="$3" extra_opts="$4"

    # disconnect any existing ACL
    sudo hcitool -i ${HCI} dc "${MAC}" 2>/dev/null || true
    sleep 0.5

    # start fresh receiver
    "${SSH}" "pidof l2test | xargs -r kill 2>/dev/null; sleep 0.2; nohup l2test -d -P ${PSM} -I 1021 ${extra_opts} > /tmp/l2test-rx.log 2>&1 & sleep 0.4"

    # snapshot RX bytes before
    local rx_before
    rx_before=$("${SSH}" "awk '/RX bytes/ { sub(/RX bytes:/,\"\"); print \$1; exit }' < <(hciconfig ${HCI})")

    # send
    local t0
    t0=$(date +%s%N)
    sudo l2test -s -P ${PSM} -O 1021 -I 1021 -N "${frames}" -b "${size}" ${extra_opts} "${MAC}" >/dev/null 2>&1 || true

    # wait for device-side RX to settle
    local last rx_now
    last=0
    local t_settle=0
    for i in $(seq 1 80); do
        rx_now=$("${SSH}" "awk '/RX bytes/ { sub(/RX bytes:/,\"\"); print \$1; exit }' < <(hciconfig ${HCI})")
        if [ "${rx_now}" = "${last}" ] && [ "${i}" -gt 3 ]; then
            t_settle=$(date +%s%N)
            break
        fi
        last="${rx_now}"
        sleep 0.25
    done
    if [ "${t_settle}" -eq 0 ]; then
        t_settle=$(date +%s%N)
    fi

    local elapsed_ns=$(( t_settle - t0 ))
    local bytes_rx=$(( rx_now - rx_before ))
    local bytes_sent=$(( frames * size ))

    if [ "${elapsed_ns}" -gt 0 ]; then
        # subtract estimated quiesce window from elapsed (the 0.25s * dead loops at the tail)
        # rough: each poll is 0.25s, we did ~3 sentinel polls, subtract 0.5s
        local elapsed_corr_ns=$(( elapsed_ns - 500000000 ))
        if [ "${elapsed_corr_ns}" -le 0 ]; then elapsed_corr_ns=${elapsed_ns}; fi
        local rate_b_per_s=$(( bytes_rx * 1000000000 / elapsed_corr_ns ))
        printf "%-24s frames=%5d size=%5d sent=%8d  rx=%8d  elapsed=%5.2fs  rate=%4d KiB/s\n" \
            "${label}" "${frames}" "${size}" "${bytes_sent}" "${bytes_rx}" \
            "$(echo "scale=2; ${elapsed_corr_ns}/1000000000" | bc)" \
            "$(( rate_b_per_s / 1024 ))"
    else
        printf "%-24s elapsed=0; failed\n" "${label}"
    fi

    "${SSH}" "pidof l2test | xargs -r kill 2>/dev/null"
}

# bluez bonding state sanity
sudo bluetoothctl info "${MAC}" 2>&1 | grep -E "Paired:|Bonded:" | sed 's/^/  /'
echo

# pure UART-link throughput (basic L2CAP, varying frame size)
echo "=== basic L2CAP, varying outgoing frame size ==="
run_test "size=256"  2000 256  ""
run_test "size=512"  2000 512  ""
run_test "size=672"  2000 672  ""   # 3-DH5 fits ~1023 byte payload
run_test "size=1021" 2000 1021 ""

echo
echo "=== ERTM L2CAP ==="
run_test "ertm/1021" 2000 1021 "-X ertm"
run_test "ertm/512"  2000 512  "-X ertm"

echo
echo "=== Streaming L2CAP (no retransmit) ==="
run_test "stream/1021" 2000 1021 "-X streaming"
