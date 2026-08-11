FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# symlink + tmpfiles for the dynamic bridgething avahi service.
SRC_URI += " \
    file://bridgething-avahi-runtime.tmpfiles.conf \
    file://avahi-no-chroot.conf \
"

do_install:append() {
    ln -sf /run/avahi/services/bridgething.service \
        ${D}${sysconfdir}/avahi/services/bridgething.service

    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${UNPACKDIR}/bridgething-avahi-runtime.tmpfiles.conf \
        ${D}${nonarch_libdir}/tmpfiles.d/bridgething-avahi-runtime.conf

    install -d ${D}${systemd_system_unitdir}/avahi-daemon.service.d
    install -m 0644 ${UNPACKDIR}/avahi-no-chroot.conf \
        ${D}${systemd_system_unitdir}/avahi-daemon.service.d/no-chroot.conf
}

FILES:${PN} += " \
    ${sysconfdir}/avahi/services/bridgething.service \
    ${nonarch_libdir}/tmpfiles.d/bridgething-avahi-runtime.conf \
    ${systemd_system_unitdir}/avahi-daemon.service.d/no-chroot.conf \
"
