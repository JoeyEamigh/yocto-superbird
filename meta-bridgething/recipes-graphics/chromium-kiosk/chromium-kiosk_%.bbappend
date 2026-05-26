do_install:append() {
    echo "KIOSK_ENV_OVERRIDE_FILE=/opt/bridgething/kiosk-env" >> ${D}${sysconfdir}/default/chromium-kiosk
}
