SUMMARY = "mDNS advertisement for the board"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://ssh.service \
    file://http.service \
    file://superbird-mdns-alias.service \
    file://no-multicast-dns.conf \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-mdns-alias.service"
SYSTEMD_AUTO_ENABLE = "enable"

# http.service ships @@MDNS_SERVICE_NAME@@ as a placeholder for the avahi <name> field.
do_install() {
    install -d ${D}${sysconfdir}/avahi/services
    install -m 0644 ${S}/ssh.service ${D}${sysconfdir}/avahi/services/ssh.service
    install -m 0644 ${S}/http.service ${D}${sysconfdir}/avahi/services/http.service
    sed -i -e 's/@@MDNS_SERVICE_NAME@@/${SUPERBIRD_MDNS_SERVICE_NAME}/g' \
        ${D}${sysconfdir}/avahi/services/http.service

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-mdns-alias.service ${D}${systemd_system_unitdir}/

    install -d ${D}${sysconfdir}/systemd/resolved.conf.d
    install -m 0644 ${S}/no-multicast-dns.conf ${D}${sysconfdir}/systemd/resolved.conf.d/
}

FILES:${PN} = "\
    ${sysconfdir}/avahi/services/ssh.service \
    ${sysconfdir}/avahi/services/http.service \
    ${sysconfdir}/systemd/resolved.conf.d/no-multicast-dns.conf \
    ${systemd_system_unitdir}/superbird-mdns-alias.service \
"

RDEPENDS:${PN} = "avahi-daemon avahi-libnss-mdns avahi-utils"
