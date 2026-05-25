SUMMARY = "Fully transparent xcursor theme for kiosk use"
DESCRIPTION = "Ships an xcursor theme where every named cursor is a 1x1 transparent pixel. Selected via XCURSOR_THEME=blank in the systemd environment."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://transparent \
    file://index.theme \
    file://blank-cursor-env.conf \
"
S = "${UNPACKDIR}"

# freedesktop cursor-spec names plus legacy aliases. missing a name means a cursor surfaces for it.
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

    install -m 0644 ${S}/transparent \
        ${D}${datadir}/icons/blank/cursors/transparent

    for name in ${CURSOR_NAMES}; do
        ln -sf transparent ${D}${datadir}/icons/blank/cursors/${name}
    done

    install -m 0644 ${S}/index.theme \
        ${D}${datadir}/icons/blank/index.theme

    install -d ${D}${sysconfdir}/environment.d
    install -m 0644 ${S}/blank-cursor-env.conf \
        ${D}${sysconfdir}/environment.d/50-blank-cursor.conf
}

FILES:${PN} = " \
    ${datadir}/icons/blank \
    ${sysconfdir}/environment.d/50-blank-cursor.conf \
"
