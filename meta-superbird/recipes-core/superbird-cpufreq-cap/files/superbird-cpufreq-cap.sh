#!/bin/sh
# Apply a CPU-frequency upper cap based on detected USB charger class.
#
# u-boot reads the MAX14656 charger-detect IC and passes the safe peak
# frequency for the detected port type via /proc/cmdline as
#   superbird.max_cpufreq_khz=NNNN
# Class -> cap table (kept in u-boot, mirror here for documentation):
#   SDP  ( 500 mA, 2.5 W)  -> 1512000  (~1.5 GHz / 791 mV)
#   SDP-HC / CDP (1500 mA) -> 1704000  (~1.7 GHz / 861 mV)
#   DCP  (>=2 A, >=10 W)   -> 1800000  (~1.8 GHz / 981 mV)
#
# If the param is absent or invalid we default to 1.5 GHz - matches
# the SDP-500 class, since that is the worst USB power source the
# Car Thing might be plugged into and the safe baseline.
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

# Sanity check (purely numeric, plausible range).
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

# Apply to every cpufreq policy. On g12a all four cores share a policy
# but write each in case future kernels split them.
APPLIED=0
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -w "$policy/scaling_max_freq" ] || continue
    echo "$CAP" > "$policy/scaling_max_freq"
    APPLIED=$((APPLIED + 1))
done

if [ "$APPLIED" -eq 0 ]; then
    echo "superbird-cpufreq-cap: no cpufreq policies found - cpufreq-dt not bound?" >&2
    exit 1
fi

echo "superbird-cpufreq-cap: scaling_max_freq=$CAP applied to $APPLIED polic(ies)"
