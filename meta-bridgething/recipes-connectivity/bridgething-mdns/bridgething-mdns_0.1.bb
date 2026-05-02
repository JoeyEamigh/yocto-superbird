SUMMARY = "Bridgething mDNS service advertisements"
DESCRIPTION = "Drops avahi service files at /etc/avahi/services/ that publish \
the device's SSH and bridgething webserver endpoints under bridgething.local. \
Pairs with the USB-gadget recipe so plugging the device in is enough to find \
it - no static-IP setup required on the host. Uses the upstream avahi conf \
defaults (host-name from /etc/hostname, all-interfaces) which fits this \
USB-only-network device cleanly."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://ssh.service \
    file://http.service \
"

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sysconfdir}/avahi/services
    install -m 0644 ${S}/ssh.service  ${D}${sysconfdir}/avahi/services/ssh.service
    install -m 0644 ${S}/http.service ${D}${sysconfdir}/avahi/services/http.service
}

FILES:${PN} = "\
    ${sysconfdir}/avahi/services/ssh.service \
    ${sysconfdir}/avahi/services/http.service \
"

RDEPENDS:${PN} = "avahi-daemon avahi-libnss-mdns"
