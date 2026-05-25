#!/bin/sh
# memory / swap / chromium pressure sampler. streams vmstat + zram + per-pid stats at 5 Hz.
# usage: /tmp/mem-sampler.sh [duration_seconds] > /tmp/mem-sampler.log  (default 60s)
# output: one event per line, "<ms> <type> <payload>".

set -u

DURATION="${1:-60}"
INTERVAL_S=0.2

now_ms() {
    awk 'BEGIN { getline u < "/proc/uptime"; split(u, a, " "); printf "%d\n", a[1] * 1000 }'
}

START_MS=$(now_ms)
END_MS=$(( START_MS + DURATION * 1000 ))

echo "# sampler start ts_ms=${START_MS} duration=${DURATION}s interval_s=${INTERVAL_S}"
echo "# uname=$(uname -r) memtotal_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
echo "# zram_disksize=$(cat /sys/block/zram0/disksize 2>/dev/null) zram_comp=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null)"
echo "# columns: ts_ms event payload..."

# global vars only; busybox has no `local`. one awk per tick beats nine.
read_vmstat() {
    eval $(awk '
        $1=="pswpin"        { printf "_pswpin=%d ",        $2 }
        $1=="pswpout"       { printf "_pswpout=%d ",       $2 }
        $1=="pgmajfault"    { printf "_pgmajfault=%d ",    $2 }
        $1=="pgfault"       { printf "_pgfault=%d ",       $2 }
        $1=="pgsteal_anon"  { printf "_pgsteal_anon=%d ",  $2 }
        $1=="pgsteal_file"  { printf "_pgsteal_file=%d ",  $2 }
        $1=="pgscan_kswapd" { printf "_pgscan_kswapd=%d ", $2 }
        $1=="pgscan_direct" { printf "_pgscan_direct=%d ", $2 }
        $1=="oom_kill"      { printf "_oom_kill=%d ",      $2 }
    ' /proc/vmstat)
}
read_vmstat
PREV_PSWPIN=$_pswpin
PREV_PSWPOUT=$_pswpout
PREV_PGMAJFAULT=$_pgmajfault
PREV_PGFAULT=$_pgfault
PREV_PGSTEAL_ANON=$_pgsteal_anon
PREV_PGSTEAL_FILE=$_pgsteal_file
PREV_PGSCAN_KSWAPD=$_pgscan_kswapd
PREV_PGSCAN_DIRECT=$_pgscan_direct
PREV_OOMKILL=${_oom_kill:-0}
PREV_ZRAM_RD=0
PREV_ZRAM_WR=0
if [ -r /sys/block/zram0/stat ]; then
    set -- $(cat /sys/block/zram0/stat)
    PREV_ZRAM_RD=$1
    PREV_ZRAM_WR=$5
fi
# /proc/stat: cpu user nice system idle iowait irq softirq steal
read _ U N S I IO IR SI ST _ < /proc/stat
PREV_CPU_BUSY=$(( U + N + S + IR + SI + ST ))
PREV_CPU_IOWAIT=$IO
PREV_CPU_TOTAL=$(( U + N + S + I + IO + IR + SI + ST ))

# per-pid prev counters as flat vars. busybox has no assoc arrays.

label_for_pid() {
    pid=$1
    cmd=$(tr '\0' ' ' < /proc/$pid/cmdline 2>/dev/null)
    case "$cmd" in
        */chromium\ *--kiosk*)         echo "browser"  ;;
        *--type=zygote*)               echo "zygote"   ;;
        *--type=gpu-process*)          echo "gpu"      ;;
        *--type=renderer*)             echo "rend"     ;;
        *--type=utility*NetworkService*) echo "netsvc" ;;
        *--type=utility*StorageService*) echo "storage" ;;
        *--type=utility*OnDeviceModel*)  echo "ondevm"  ;;
        *--type=utility*)              echo "util"    ;;
        *crashpad_handler*)            echo "crashp"  ;;
        *)                             echo "other"   ;;
    esac
}

emit_meminfo() {
    awk -v rel="$1" '
        /^MemFree:/      { mf=$2 }
        /^MemAvailable:/ { ma=$2 }
        /^Buffers:/      { bu=$2 }
        /^Cached:/       { ca=$2 }
        /^SwapFree:/     { sf=$2 }
        /^Dirty:/        { dy=$2 }
        /^AnonPages:/    { an=$2 }
        /^Mapped:/       { mp=$2 }
        END { printf "%d meminfo free=%d avail=%d buf=%d cached=%d swapfree=%d dirty=%d anon=%d mapped=%d\n",
              rel, mf, ma, bu, ca, sf, dy, an, mp }
    ' /proc/meminfo
}

emit_vmstat_deltas() {
    rel=$1
    read_vmstat
    oomk=${_oom_kill:-0}
    printf "%d vmstat swpin=%d swpout=%d majflt=%d flt=%d steal_anon=%d steal_file=%d scan_kswap=%d scan_direct=%d oom=%d\n" \
        $rel \
        $(( _pswpin        - PREV_PSWPIN )) \
        $(( _pswpout       - PREV_PSWPOUT )) \
        $(( _pgmajfault    - PREV_PGMAJFAULT )) \
        $(( _pgfault       - PREV_PGFAULT )) \
        $(( _pgsteal_anon  - PREV_PGSTEAL_ANON )) \
        $(( _pgsteal_file  - PREV_PGSTEAL_FILE )) \
        $(( _pgscan_kswapd - PREV_PGSCAN_KSWAPD )) \
        $(( _pgscan_direct - PREV_PGSCAN_DIRECT )) \
        $(( oomk           - PREV_OOMKILL ))
    PREV_PSWPIN=$_pswpin
    PREV_PSWPOUT=$_pswpout
    PREV_PGMAJFAULT=$_pgmajfault
    PREV_PGFAULT=$_pgfault
    PREV_PGSTEAL_ANON=$_pgsteal_anon
    PREV_PGSTEAL_FILE=$_pgsteal_file
    PREV_PGSCAN_KSWAPD=$_pgscan_kswapd
    PREV_PGSCAN_DIRECT=$_pgscan_direct
    PREV_OOMKILL=$oomk
}

emit_zram() {
    rel=$1
    [ -r /sys/block/zram0/mm_stat ] || return
    read orig comp used limit max_used same compacted huge < /sys/block/zram0/mm_stat
    set -- $(cat /sys/block/zram0/stat)
    rd=$1
    wr=$5
    rd_sect=$3
    wr_sect=$7
    printf "%d zram orig_kb=%d comp_kb=%d used_kb=%d ratio_x100=%d d_rd=%d d_wr=%d d_rd_sect=%d d_wr_sect=%d\n" \
        $rel \
        $((orig/1024)) $((comp/1024)) $((used/1024)) \
        $(( comp > 0 ? (orig*100)/comp : 0 )) \
        $(( rd - PREV_ZRAM_RD )) \
        $(( wr - PREV_ZRAM_WR )) \
        $(( rd_sect - 0 )) \
        $(( wr_sect - 0 ))
    PREV_ZRAM_RD=$rd
    PREV_ZRAM_WR=$wr
}

emit_cpu_total() {
    rel=$1
    read _ U N S I IO IR SI ST _ < /proc/stat
    busy=$(( U + N + S + IR + SI + ST ))
    iowait=$IO
    total=$(( U + N + S + I + IO + IR + SI + ST ))
    d_busy=$(( busy - PREV_CPU_BUSY ))
    d_iowait=$(( iowait - PREV_CPU_IOWAIT ))
    d_total=$(( total - PREV_CPU_TOTAL ))
    [ $d_total -le 0 ] && d_total=1
    printf "%d cputot busy_pct=%d iowait_pct=%d d_total=%d\n" \
        $rel \
        $(( d_busy * 100 / d_total )) \
        $(( d_iowait * 100 / d_total )) \
        $d_total
    PREV_CPU_BUSY=$busy
    PREV_CPU_IOWAIT=$iowait
    PREV_CPU_TOTAL=$total
}

emit_loadavg() {
    rel=$1
    read l1 l5 l15 _ < /proc/loadavg
    printf "%d loadavg l1=%s l5=%s l15=%s\n" $rel "$l1" "$l5" "$l15"
}

emit_cpufreq() {
    rel=$1
    cur=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null || echo 0)
    gov=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo unknown)
    printf "%d cpufreq cpu0_khz=%s gov=%s\n" $rel "$cur" "$gov"
}

emit_pid() {
    rel=$1
    pid=$2
    label=$3
    [ -r /proc/$pid/status ] || return
    [ -r /proc/$pid/stat ]   || return
    rss=$(awk '/^VmRSS:/ {print $2; exit}' /proc/$pid/status)
    swap=$(awk '/^VmSwap:/ {print $2; exit}' /proc/$pid/status)
    anon=$(awk '/^RssAnon:/ {print $2; exit}' /proc/$pid/status)
    file=$(awk '/^RssFile:/ {print $2; exit}' /proc/$pid/status)
    state=$(awk '/^State:/ {print $2; exit}' /proc/$pid/status)
    # /proc/<pid>/stat trimmed of comm: state ppid pgrp ... minflt cminflt majflt cmajflt utime stime
    rest=$(sed 's/.*) //' /proc/$pid/stat 2>/dev/null)
    set -- $rest
    minflt=$8
    majflt=${10}
    utime=${12}
    stime=${13}
    pmaj_var="PID_${pid}_MAJFLT"
    pmin_var="PID_${pid}_MINFLT"
    pu_var="PID_${pid}_UTIME"
    ps_var="PID_${pid}_STIME"
    eval "prev_maj=\${$pmaj_var:-$majflt}"
    eval "prev_min=\${$pmin_var:-$minflt}"
    eval "prev_u=\${$pu_var:-$utime}"
    eval "prev_s=\${$ps_var:-$stime}"
    eval "$pmaj_var=$majflt"
    eval "$pmin_var=$minflt"
    eval "$pu_var=$utime"
    eval "$ps_var=$stime"
    d_maj=$(( majflt - prev_maj ))
    d_min=$(( minflt - prev_min ))
    d_u=$(( utime - prev_u ))
    d_s=$(( stime - prev_s ))
    printf "%d pid pid=%d label=%s state=%s rss=%d anon=%d file=%d swap=%d d_maj=%d d_min=%d d_u=%d d_s=%d\n" \
        $rel $pid "$label" "$state" \
        $rss $anon $file $swap \
        $d_maj $d_min $d_u $d_s
    # stack snapshot only when this pid burned cpu this tick
    if [ $d_u -gt 0 ] || [ $d_s -gt 0 ] || [ $d_maj -gt 0 ]; then
        if [ -r /proc/$pid/stack ]; then
            top=$(head -3 /proc/$pid/stack 2>/dev/null | awk '{printf "%s|", $2}')
            printf "%d pidstack pid=%d label=%s top=%s\n" $rel $pid "$label" "$top"
        fi
    fi
}

# rediscover chromium pids each tick; renderers spawn and die.
discover_chromium_pids() {
    pgrep -f chromium 2>/dev/null
}

while :; do
    ts=$(now_ms)
    [ "$ts" -ge "$END_MS" ] && break
    rel=$(( ts - START_MS ))

    emit_meminfo "$rel"
    emit_vmstat_deltas "$rel"
    emit_zram "$rel"
    emit_cpu_total "$rel"
    emit_loadavg "$rel"
    emit_cpufreq "$rel"

    for pid in $(discover_chromium_pids); do
        label=$(label_for_pid $pid)
        emit_pid "$rel" "$pid" "$label"
    done

    sleep $INTERVAL_S
done

echo "# sampler end ts_ms=$(now_ms)"
