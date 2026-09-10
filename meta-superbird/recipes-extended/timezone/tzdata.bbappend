INSTALL_TIMEZONE_FILE = "0"

do_install:append() {
    rm -f ${D}${sysconfdir}/localtime
}

FILES:tzdata-core:remove = "${sysconfdir}/localtime ${sysconfdir}/timezone"
