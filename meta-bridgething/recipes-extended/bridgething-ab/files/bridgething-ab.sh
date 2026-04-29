#!/bin/sh
# Bridgething A/B slot management CLI.
#
# v1 surface for testing the OTA flow. The bridgething daemon will
# eventually subsume this (see lib/ab/ in the bridgething repo) and
# expose the same operations over the gateway WebSocket - until
# then this script is the canonical way to drive a slot flip from
# the device side.
#
# Subcommands:
#   status                 print active + inactive slot, image build id, try counters
#   active                 print "_a" or "_b"
#   inactive               print the OTHER slot
#   set-slot <_a|_b>       fw_setenv active_slot=<slot>
#   apply <swu>            swupdate-client into the INACTIVE slot;
#                          on success: reset that slot's try counter,
#                          flip active_slot, set want_boot=kernel so
#                          the next reboot actually attempts the
#                          new image
#   apply-and-reboot <swu> apply then reboot if successful
#   boot-confirm           called by bridgething-boot-confirm.service
#                          after multi-user.target. resets the
#                          ACTIVE slot's try counter to 3 (we made
#                          it this far without panicking) and flips
#                          want_boot back to burn so dev iteration
#                          via reset-pulse stays safe.

set -eu

TRY_MAX=3

usage() {
    grep '^#   ' "$0" | sed 's/^#   /  /'
    exit 2
}

slot_active() {
    fw_printenv -n active_slot 2>/dev/null || echo "_a"
}

slot_inactive() {
    case "$(slot_active)" in
        _a) echo "_b" ;;
        _b) echo "_a" ;;
        *) echo "_b" ;;
    esac
}

slot_try() {
    case "$1" in
        _a) fw_printenv -n slot_a_try 2>/dev/null || echo "?" ;;
        _b) fw_printenv -n slot_b_try 2>/dev/null || echo "?" ;;
    esac
}

case "${1-}" in
    status)
        active="$(slot_active)"
        inactive="$(slot_inactive)"
        printf "active:    %s  (try=%s)\n" "$active"   "$(slot_try "$active")"
        printf "inactive:  %s  (try=%s)\n" "$inactive" "$(slot_try "$inactive")"
        printf "want_boot: %s\n" "$(fw_printenv -n want_boot 2>/dev/null || echo "?")"
        if [ -r /etc/superbird ]; then
            build_id=$(grep -o '"imageBuildId"[^,]*' /etc/superbird | sed 's/.*: *"\(.*\)".*/\1/')
            printf "build:     %s\n" "$build_id"
        fi
        ;;
    active)   slot_active ;;
    inactive) slot_inactive ;;
    set-slot)
        slot="${2-}"
        case "$slot" in
            _a|_b) fw_setenv active_slot "$slot" ;;
            *) echo "set-slot: expected _a or _b, got '$slot'" >&2; exit 2 ;;
        esac
        ;;
    apply)
        swu="${2-}"
        [ -f "$swu" ] || { echo "apply: file not found: $swu" >&2; exit 2; }
        target="$(slot_inactive)"
        # selector matches sw-description's `slot_a` / `slot_b`
        # streams (without the leading underscore).
        sel="slot${target}"
        echo "applying $swu into ${target} (selector: stable,${sel})"
        if swupdate -i "$swu" -e "stable,${sel}"; then
            echo "swupdate succeeded - slot flipped via bootenv to ${target}"
            # active_slot is set inside swupdate's bootenv block on
            # successful install (atomic with the partition writes).
            # We still own boot policy:
            #   - reset try counter for the new slot so a panic on
            #     first boot of the new image triggers the
            #     boot_safety swap back rather than instantly
            #     starving;
            #   - flip want_boot=kernel so the next reboot actually
            #     attempts the kernel (default is burn).
            case "$target" in
                _a) fw_setenv slot_a_try "$TRY_MAX" ;;
                _b) fw_setenv slot_b_try "$TRY_MAX" ;;
            esac
            fw_setenv want_boot kernel
        else
            rc=$?
            echo "swupdate failed (exit $rc); active_slot left as $(slot_active)" >&2
            exit "$rc"
        fi
        ;;
    apply-and-reboot)
        swu="${2-}"
        [ -f "$swu" ] || { echo "apply-and-reboot: file not found: $swu" >&2; exit 2; }
        "$0" apply "$swu"
        echo "applied. rebooting."
        sync
        systemctl reboot
        ;;
    boot-confirm)
        # We're alive enough to reach multi-user.target - reset the
        # active slot's try counter so a future panic loop on this
        # slot starts with a fresh budget. want_boot stays "kernel"
        # (the env-default now): every reset goes back into the
        # kernel branch unless userland explicitly flips it via
        # `fw_setenv want_boot burn && reboot`.
        active="$(slot_active)"
        case "$active" in
            _a) fw_setenv slot_a_try "$TRY_MAX" ;;
            _b) fw_setenv slot_b_try "$TRY_MAX" ;;
        esac
        echo "boot-confirm: slot${active}_try=${TRY_MAX}"
        ;;
    -h|--help|help|"")
        usage
        ;;
    *)
        echo "unknown command: $1" >&2
        usage
        ;;
esac
