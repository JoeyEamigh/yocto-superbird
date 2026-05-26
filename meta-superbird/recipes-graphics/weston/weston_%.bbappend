FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# split vnc-backend.so into its own package so prod doesn't carry it; dev rdepends on it
PACKAGES =+ "${PN}-vnc-backend"

# co-package the pam_permit weston-remote-access service alongside vnc-backend.so
FILES:${PN}-vnc-backend = "${libdir}/libweston-${WESTON_MAJOR_VERSION}/vnc-backend.so \
                           ${sysconfdir}/pam.d/weston-remote-access"

RDEPENDS:${PN}-vnc-backend = "neatvnc aml libpam"

# poky's PACKAGECONFIG[vnc] misses libaml which weston-15 backend-vnc requires
DEPENDS:append = " aml"

# pam_permit variant of weston-remote-access
SRC_URI:append = " file://weston-remote-access.pam"

# 0001: drop the getpwnam guard so pam_permit is the sole vnc auth gate
# 0002: repick pointer focus after lazy seat init so wheel-only rotary delivers wl_pointer.axis
# 0003: alpha-zero the cursor sprite at set_cursor so the first frame doesn't flash a cursor
# 0004: eager pointer-cap init at device-add so a wheel-only device exposes pointer immediately
SRC_URI:append = " file://0001-backend-vnc-skip-getpwnam-guard-in-vnc_handle_auth.patch"
SRC_URI:append = " file://0002-libinput-pick-pointer-focus-after-lazy-pointer-init.patch"
SRC_URI:append = " file://0003-input-zero-cursor-sprite-alpha-on-set-cursor.patch"
SRC_URI:append = " file://0004-libinput-eager-pointer-cap-init-at-device-add.patch"

do_install:append() {
    install -m 0644 ${UNPACKDIR}/weston-remote-access.pam \
        ${D}${sysconfdir}/pam.d/weston-remote-access
}
