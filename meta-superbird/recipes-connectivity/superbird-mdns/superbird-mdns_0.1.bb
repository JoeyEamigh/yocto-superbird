SUMMARY = "Static avahi service files (ssh + http)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "\
    file://ssh.service \
    file://http.service \
"

S = "${UNPACKDIR}"

# http.service ships @@MDNS_SERVICE_NAME@@ as a placeholder for the avahi <name> field.
do_install() {
    install -d ${D}${sysconfdir}/avahi/services
    install -m 0644 ${S}/ssh.service ${D}${sysconfdir}/avahi/services/ssh.service
    install -m 0644 ${S}/http.service ${D}${sysconfdir}/avahi/services/http.service
    sed -i -e 's/@@MDNS_SERVICE_NAME@@/${SUPERBIRD_MDNS_SERVICE_NAME}/g' \
        ${D}${sysconfdir}/avahi/services/http.service
}

FILES:${PN} = "\
    ${sysconfdir}/avahi/services/ssh.service \
    ${sysconfdir}/avahi/services/http.service \
"

RDEPENDS:${PN} = "avahi-daemon avahi-libnss-mdns"
