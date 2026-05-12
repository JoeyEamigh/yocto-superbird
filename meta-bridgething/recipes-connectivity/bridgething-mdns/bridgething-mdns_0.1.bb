SUMMARY = "Bridgething mDNS service advertisements"
DESCRIPTION = "Drops avahi service files at /etc/avahi/services/ that publish \
the device's SSH, bridgething webserver, and bridgething gateway endpoints \
under bridgething.local. Pairs with the USB-gadget recipe so plugging the \
device in is enough to find it - no static-IP setup required on the host. \
The bridgething.service entry is a symlink to /run/avahi/services/ - the \
bridgething daemon writes that path at startup and on every nickname \
change so the service announcement carries a fresh nickname TXT record. \
Uses the upstream avahi conf defaults (host-name from /etc/hostname, \
all-interfaces) which fits this USB-only-network device cleanly."
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
    # The bridgething.service file is daemon-written at runtime (carries
    # the dynamic nickname TXT record). avahi follows symlinks; the
    # symlink target lives in /run so the rootfs stays read-only.
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
