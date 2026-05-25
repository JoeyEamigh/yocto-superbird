SUMMARY = "Persistent /opt/bridgething overlay backed by the settings partition"
DESCRIPTION = "Bind-mounts /var/lib/bridgething/persist (settings partition, shared across system_a/b) onto /opt/bridgething so the daemon binary and installed webapps survive bootslot swaps and OTAs."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-opt-overlay.service \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-opt-overlay.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-opt-overlay.service \
        ${D}${systemd_system_unitdir}/bridgething-opt-overlay.service

    # bind-mount target must exist in the ro rootfs at flash time
    install -d ${D}/opt/bridgething
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/bridgething-opt-overlay.service \
    /opt \
    /opt/bridgething \
"
