FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# enables CONFIG_BOOTLOADERHANDLER + CONFIG_SYSTEMD; ships the delta-handler seek patch
SRC_URI += " \
    file://bridgething.cfg \
    file://0001-delta_handler-don-t-apply-img-seek-to-zchunk-header-.patch \
"
