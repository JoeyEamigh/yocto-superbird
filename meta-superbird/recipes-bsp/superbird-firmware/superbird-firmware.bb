SUMMARY = "Superbird non-redistributable firmware blobs"
DESCRIPTION = "Ships proprietary firmware extracted from stock Spotify Car Thing images. \
Currently provides Broadcom BCM20703A2 Bluetooth controller firmware."
LICENSE = "CLOSED"

SRC_URI = "file://brcm/BCM20703A2.hcd"

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware/brcm
    install -m 0644 ${S}/brcm/BCM20703A2.hcd ${D}${nonarch_base_libdir}/firmware/brcm/BCM20703A2.hcd
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/brcm/BCM20703A2.hcd"
