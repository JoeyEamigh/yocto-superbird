# point the kiosk launcher at a runtime override path inside the vendor's
# opt-overlay. drop key=value lines (KIOSK_URL=..., KIOSK_PROXY_SERVER=...,
# KIOSK_EXTRA=...) here to retarget without a rebuild.
do_install:append() {
    echo "KIOSK_ENV_OVERRIDE_FILE=/opt/superbird-kiosk/kiosk-env" >> ${D}${sysconfdir}/default/chromium-kiosk
}
