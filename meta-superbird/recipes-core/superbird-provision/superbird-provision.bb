SUMMARY = "First-boot eMMC data-partition provisioning"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-provision.sh \
    file://superbird-provision.service \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-provision.service"
SYSTEMD_AUTO_ENABLE = "enable"

# sgdisk (GPT editor) is the only runtime tool the script needs.
RDEPENDS:${PN} = "gptfdisk"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/superbird-provision.sh ${D}${libexecdir}/superbird-provision

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/superbird-provision.service \
        ${D}${systemd_system_unitdir}/superbird-provision.service
}

FILES:${PN} = " \
    ${libexecdir}/superbird-provision \
    ${systemd_system_unitdir}/superbird-provision.service \
"
