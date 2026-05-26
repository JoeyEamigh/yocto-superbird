# point the kiosk launcher at a runtime override file. third-party kiosks
# can drop key=value lines (KIOSK_URL=..., KIOSK_PROXY_SERVER=..., KIOSK_EXTRA=...)
# at this path to retarget without rebuilding the image.
do_install:append() {
    echo "KIOSK_ENV_OVERRIDE_FILE=/etc/kiosk-overrides.env" >> ${D}${sysconfdir}/default/chromium-kiosk
}
