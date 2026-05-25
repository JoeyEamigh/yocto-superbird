#!/bin/sh
# apply scaling_max_freq from superbird.max_cpufreq_khz= on the kernel cmdline (set by u-boot from charger class).
set -eu

DEFAULT_KHZ=1512000

CAP="$DEFAULT_KHZ"
for arg in $(cat /proc/cmdline); do
    case "$arg" in
        superbird.max_cpufreq_khz=*)
            CAP="${arg#superbird.max_cpufreq_khz=}"
            ;;
    esac
done

case "$CAP" in
    ""|*[!0-9]*)
        echo "superbird-cpufreq-cap: invalid \"$CAP\", using $DEFAULT_KHZ"
        CAP="$DEFAULT_KHZ"
        ;;
esac
if [ "$CAP" -lt 1000000 ] || [ "$CAP" -gt 1800000 ]; then
    echo "superbird-cpufreq-cap: out-of-range $CAP, using $DEFAULT_KHZ"
    CAP="$DEFAULT_KHZ"
fi

APPLIED=0
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -w "$policy/scaling_max_freq" ] || continue
    echo "$CAP" > "$policy/scaling_max_freq"
    APPLIED=$((APPLIED + 1))
done

if [ "$APPLIED" -eq 0 ]; then
    echo "superbird-cpufreq-cap: no cpufreq policies found" >&2
    exit 1
fi

echo "superbird-cpufreq-cap: scaling_max_freq=$CAP applied to $APPLIED polic(ies)"
