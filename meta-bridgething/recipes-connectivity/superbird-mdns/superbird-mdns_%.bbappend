FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# symlink + tmpfiles for the dynamic bridgething avahi service.
SRC_URI += " \
    file://bridgething-avahi-runtime.tmpfiles.conf \
"

do_install:append() {
    ln -sf /run/avahi/services/bridgething.service \
        ${D}${sysconfdir}/avahi/services/bridgething.service

    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${UNPACKDIR}/bridgething-avahi-runtime.tmpfiles.conf \
        ${D}${nonarch_libdir}/tmpfiles.d/bridgething-avahi-runtime.conf
}

FILES:${PN} += " \
    ${sysconfdir}/avahi/services/bridgething.service \
    ${nonarch_libdir}/tmpfiles.d/bridgething-avahi-runtime.conf \
"
