#!/bin/sh
# Sample resource metrics for a browser process tree until it exits or this
# script is interrupted. Writes per-sample CSV + a summary text file.
#
# Usage:
#   browser-bench.sh <label> <pid>
#   browser-bench.sh <label> --launch -- <cmd...>     (wraps via systemd-run)
#
# Examples:
#   browser-bench.sh cog-spd3 --launch -- wsh cog -P wl --window-fullscreen \
#     https://browserbench.org/Speedometer3.1/
#   browser-bench.sh chr-spd3 12345
#
# In --launch mode the command runs as transient unit `bench-<label>` so it
# survives ssh disconnect. Stop it via `systemctl stop bench-<label>`.

set -eu

label=$1; shift
case "${1:-}" in
    --launch)
        shift
        [ "${1:-}" = "--" ] || { echo "after --launch expected --, then cmd" >&2; exit 2; }
        shift
        unit="bench-$label"
        systemctl reset-failed "$unit" 2>/dev/null || true
        systemd-run --unit="$unit" --quiet \
            --setenv=XDG_RUNTIME_DIR=/run/weston \
            --setenv=WAYLAND_DISPLAY=wayland-1 \
            --setenv=HOME=/root \
            "$@"
        sleep 1
        BPID=$(systemctl show -p MainPID --value "$unit")
        if [ -z "$BPID" ] || [ "$BPID" = "0" ]; then
            echo "failed to launch $unit" >&2
            journalctl -u "$unit" -n 20 --no-pager >&2
            exit 1
        fi
        echo "==> launched $unit pid=$BPID"
        ;;
    *)
        BPID=$1; shift
        ;;
esac

POLL=${POLL_S:-2}
OUT=${OUT_DIR:-/var/log/browser-bench}
mkdir -p "$OUT"
RUN="$OUT/$label-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN"

{
    echo "label=$label"
    echo "pid=$BPID"
    echo "poll_s=$POLL"
    echo "date=$(date -Iseconds)"
    echo "kernel=$(uname -r)"
    echo "mem_total_kB=$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
    grep -E '^Swap' /proc/swaps 2>/dev/null || echo "(no swap)"
} > "$RUN/meta.txt"

cat /proc/meminfo > "$RUN/meminfo.before"
free > "$RUN/free.before"

csv="$RUN/metrics.csv"
echo "ts,wall_s,brwsr_pids,brwsr_rss_kB,brwsr_cpu_pct,sys_avail_kB,sys_used_kB,sys_swap_used_kB,gpu_freq_hz,panfrost_clients,loadavg_1" > "$csv"

t0=$(date +%s)
prev_total_jiffies=0
prev_brwsr_jiffies=0
prev_have_baseline=0

while kill -0 "$BPID" 2>/dev/null; do
    now=$(date +%s)
    wall=$((now - t0))

    pids=$(ps -eo pid,ppid 2>/dev/null | awk -v root="$BPID" '
        BEGIN { p[root]=1 }
        { all[$1]=$2 }
        END {
            changed=1
            while (changed) {
                changed=0
                for (pid in all) if (!(pid in p) && (all[pid] in p)) { p[pid]=1; changed=1 }
            }
            for (pid in p) print pid
        }')

    pid_count=$(echo "$pids" | wc -w)
    rss_kb=0
    brwsr_jiffies=0
    for pid in $pids; do
        [ -r "/proc/$pid/status" ] || continue
        rss=$(awk '/^VmRSS:/ {print $2}' "/proc/$pid/status" 2>/dev/null || echo 0)
        rss_kb=$((rss_kb + ${rss:-0}))
        [ -r "/proc/$pid/stat" ] || continue
        j=$(awk '{print $14 + $15}' "/proc/$pid/stat" 2>/dev/null || echo 0)
        brwsr_jiffies=$((brwsr_jiffies + j))
    done

    total_jiffies=$(awk '/^cpu / {s=0; for (i=2;i<=NF;i++) s+=$i; print s; exit}' /proc/stat)
    if [ "$prev_have_baseline" = "1" ]; then
        dj=$((total_jiffies - prev_total_jiffies))
        bj=$((brwsr_jiffies - prev_brwsr_jiffies))
        if [ "$dj" -gt 0 ]; then
            cpu_pct=$(awk -v b="$bj" -v t="$dj" 'BEGIN { printf "%.1f", (b * 100.0) / t }')
        else
            cpu_pct=0
        fi
    else
        cpu_pct=0
    fi
    prev_total_jiffies=$total_jiffies
    prev_brwsr_jiffies=$brwsr_jiffies
    prev_have_baseline=1

    avail_kb=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
    used_kb=$(awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2} END {print t-a}' /proc/meminfo)
    swap_used_kb=$(awk '/^SwapTotal:/ {t=$2} /^SwapFree:/ {f=$2} END {print t-f}' /proc/meminfo)

    gpu_freq=$(cat /sys/class/devfreq/ffe40000.bifrost/cur_freq 2>/dev/null || echo 0)
    pf_clients=0
    if [ -r /sys/kernel/debug/dri/0/clients ]; then
        pf_clients=$(awk 'NR>1 {n++} END {print n+0}' /sys/kernel/debug/dri/0/clients)
    fi

    loadavg=$(awk '{print $1}' /proc/loadavg)

    echo "$now,$wall,$pid_count,$rss_kb,$cpu_pct,$avail_kb,$used_kb,$swap_used_kb,$gpu_freq,$pf_clients,$loadavg" >> "$csv"

    sleep "$POLL"
done

cat /proc/meminfo > "$RUN/meminfo.after"
free > "$RUN/free.after"

awk -F, 'NR==1 {next}
    {
        if ($4>peak_rss) peak_rss=$4
        rss_sum+=$4; n++
        if ($5>peak_cpu) peak_cpu=$5
        cpu_sum+=$5
        if ($8>peak_swap) peak_swap=$8
    }
    END {
        if (n==0) { print "no samples (browser exited too fast)"; exit }
        printf "samples=%d\n", n
        printf "rss_peak_MiB=%.1f\n", peak_rss/1024
        printf "rss_avg_MiB=%.1f\n", (rss_sum/n)/1024
        printf "cpu_peak_pct=%.1f\n", peak_cpu
        printf "cpu_avg_pct=%.1f\n", cpu_sum/n
        printf "swap_peak_MiB=%.1f\n", peak_swap/1024
    }' "$csv" > "$RUN/summary.txt"

echo "==> done. summary:"
cat "$RUN/summary.txt"
echo "==> full run dir: $RUN"
