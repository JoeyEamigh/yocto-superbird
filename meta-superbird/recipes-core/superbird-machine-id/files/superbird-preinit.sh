#!/bin/sh
PATH=/usr/sbin:/usr/bin:/sbin:/bin

mount -t sysfs sysfs /sys
machine_id=$(/usr/libexec/superbird-machine-id)
umount /sys

if [ -z "$machine_id" ]; then
    echo "<4>superbird-preinit: efuse serial unreadable, machine-id is transient" > /dev/kmsg
    exec /sbin/init "$@"
fi
exec /sbin/init --machine-id="$machine_id" "$@"
