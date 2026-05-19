SUMMARY = "Fully transparent xcursor theme for kiosk use"
DESCRIPTION = "Ships /usr/share/icons/blank/, an xcursor theme where every \
named cursor (left_ptr, hand2, xterm, ...) is a 1x1 fully transparent \
pixel. With XCURSOR_THEME=blank set in the systemd environment, every \
wayland client (weston-desktop-shell, chromium) picks up the blank \
theme and renders an invisible cursor — even when an input device \
synthesizes pointer events (the Car Thing's rotary scroll wheel emits \
REL_HWHEEL via libinput, which surfaces a cursor in shells that draw \
the default theme cursor on focus). \
\
Background: an earlier wayland-client (bridgething-cursor-hide.c) that \
called wl_pointer.set_cursor(NULL) on every focus event was a dead end. \
That call only affects the calling client's surfaces, not the global \
cursor. The compositor picks the cursor based on whichever client owns \
the focused surface, and shells fall back to the configured cursor \
theme when no client sets one. Killing the cursor at the theme level \
covers every path a cursor could be drawn through. \
\
Source xcursor file is from celly/transparent-xcursor (a 1x1 \
transparent PNG packed by xcursorgen, 68 bytes total). All standard \
cursor names alias to it via symlinks."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://transparent \
    file://index.theme \
    file://blank-cursor-env.conf \
"
S = "${UNPACKDIR}"

# Standard X11 cursor names per the cursor-spec freedesktop list, plus
# the few legacy names (left_ptr, top_left_arrow, etc.) that older
# clients still ask for. Adding a new name here costs ~30 bytes (one
# symlink). Missing one means a cursor surfaces for that specific
# tool/operation.
CURSOR_NAMES = " \
    default left_ptr arrow top_left_arrow X_cursor draft \
    crosshair plus cross cross_reverse tcross \
    watch wait progress \
    help question_arrow whats_this \
    hand hand1 hand2 pointer pointing_hand \
    openhand grab \
    closedhand grabbing dnd-move dnd-copy dnd-link dnd-no-drop dnd-none \
    ibeam xterm text vertical-text \
    move fleur all-scroll \
    not-allowed no-drop forbidden circle pirate \
    nw-resize n-resize ne-resize w-resize e-resize \
    sw-resize s-resize se-resize \
    ew-resize ns-resize nesw-resize nwse-resize \
    col-resize row-resize size_ver size_hor \
    size_fdiag size_bdiag size_all \
    sb_h_double_arrow sb_v_double_arrow \
    sb_left_arrow sb_right_arrow sb_up_arrow sb_down_arrow \
    top_side bottom_side left_side right_side \
    top_left_corner top_right_corner bottom_left_corner bottom_right_corner \
    copy link alias \
    zoom-in zoom-out color-picker target \
    cell context-menu wayland-cursor \
"

do_install() {
    install -d ${D}${datadir}/icons/blank/cursors

    # The xcursor binary itself goes once at the canonical path.
    install -m 0644 ${S}/transparent \
        ${D}${datadir}/icons/blank/cursors/transparent

    # Every named cursor symlinks to the same xcursor file. Symlinks
    # cost ~30 bytes each on squashfs (compressed), so the full set
    # of ~60 names fits in a couple of KB.
    for name in ${CURSOR_NAMES}; do
        ln -sf transparent ${D}${datadir}/icons/blank/cursors/${name}
    done

    install -m 0644 ${S}/index.theme \
        ${D}${datadir}/icons/blank/index.theme

    # systemd-environment-d-generator reads /etc/environment.d/*.conf
    # and exports the vars to every service in the system slice. This
    # is the simplest way to get XCURSOR_THEME=blank into chromium-kiosk,
    # weston, and anything else launched via systemd.
    install -d ${D}${sysconfdir}/environment.d
    install -m 0644 ${S}/blank-cursor-env.conf \
        ${D}${sysconfdir}/environment.d/50-blank-cursor.conf
}

FILES:${PN} = " \
    ${datadir}/icons/blank \
    ${sysconfdir}/environment.d/50-blank-cursor.conf \
"
