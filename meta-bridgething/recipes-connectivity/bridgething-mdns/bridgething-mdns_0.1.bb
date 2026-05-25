SUMMARY = "Bridgething mDNS service advertisements"
DESCRIPTION = "Avahi service files announcing the device's SSH, webserver, and gateway endpoints under bridgething.local. The daemon rewrites bridgething.service in /run on nickname changes."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://ssh.service \
    file://http.service \
    file://bridgething-mdns.tmpfiles.conf \
"

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sysconfdir}/avahi/services
    install -m 0644 ${S}/ssh.service         ${D}${sysconfdir}/avahi/services/ssh.service
    install -m 0644 ${S}/http.service        ${D}${sysconfdir}/avahi/services/http.service
    # daemon writes bridgething.service to /run at runtime so the ro rootfs stays clean
    ln -sf /run/avahi/services/bridgething.service \
        ${D}${sysconfdir}/avahi/services/bridgething.service

    install -d ${D}${nonarch_libdir}/tmpfiles.d
    install -m 0644 ${S}/bridgething-mdns.tmpfiles.conf \
        ${D}${nonarch_libdir}/tmpfiles.d/bridgething-mdns.conf
}

FILES:${PN} = "\
    ${sysconfdir}/avahi/services/ssh.service \
    ${sysconfdir}/avahi/services/http.service \
    ${sysconfdir}/avahi/services/bridgething.service \
    ${nonarch_libdir}/tmpfiles.d/bridgething-mdns.conf \
"

RDEPENDS:${PN} = "avahi-daemon avahi-libnss-mdns"
