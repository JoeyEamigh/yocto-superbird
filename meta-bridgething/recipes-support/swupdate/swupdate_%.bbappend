FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Drop in our config fragment so swupdate's CONFIG_BOOTLOADERHANDLER
# gets enabled - registers the "uboot" / "bootenv" handlers the
# parser looks for before accepting a sw-description bootenv block.
SRC_URI += " \
    file://bridgething.cfg \
    file://swupdate-type-simple.conf \
    file://0001-delta_handler-don-t-apply-img-seek-to-zchunk-header-.patch \
"

do_install:append() {
    # Drop a service drop-in that flips swupdate.service from
    # Type=notify to Type=simple. We can't enable CONFIG_SYSTEMD in
    # swupdate (sysroot quirk), so the daemon never signals READY=1;
    # without this override the unit sits in 'activating' until
    # its start timeout fires, blocking multi-user.target.
    install -d ${D}${systemd_system_unitdir}/swupdate.service.d
    install -m 0644 ${UNPACKDIR}/swupdate-type-simple.conf \
        ${D}${systemd_system_unitdir}/swupdate.service.d/type-simple.conf
}

FILES:${PN} += "${systemd_system_unitdir}/swupdate.service.d/type-simple.conf"
