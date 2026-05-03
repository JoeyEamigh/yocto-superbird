FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Drop in our config fragment so swupdate's CONFIG_BOOTLOADERHANDLER
# gets enabled (registers the "uboot" / "bootenv" handlers the parser
# looks for before accepting a sw-description bootenv block) plus
# CONFIG_SYSTEMD (sd_notify + working socket activation - see fragment
# for the IPC-connect-ENOENT story).
SRC_URI += " \
    file://bridgething.cfg \
    file://0001-delta_handler-don-t-apply-img-seek-to-zchunk-header-.patch \
"
