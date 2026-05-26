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

RDEPENDS:${PN} = "gptfdisk"

do_install() {
    install -d ${D}${libexecdir}
    install -m 0755 ${UNPACKDIR}/superbird-provision.sh ${D}${libexecdir}/superbird-provision

    sed -i \
        -e "s|@@REQUIRE_HEADROOM@@|${SUPERBIRD_REQUIRE_OTA_HEADROOM}|g" \
        -e "s|@@BOOT_PART_MIB@@|${SUPERBIRD_BOOT_PART_SIZE}|g" \
        -e "s|@@ROOT_PART_MIB@@|${SUPERBIRD_ROOT_PART_SIZE}|g" \
        -e "s|@@MARGIN_MIB@@|${SUPERBIRD_OTA_HEADROOM_MARGIN_SIZE}|g" \
        ${D}${libexecdir}/superbird-provision

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/superbird-provision.service \
        ${D}${systemd_system_unitdir}/superbird-provision.service
}

do_install[vardeps] += " \
    SUPERBIRD_REQUIRE_OTA_HEADROOM \
    SUPERBIRD_BOOT_PART_SIZE \
    SUPERBIRD_ROOT_PART_SIZE \
    SUPERBIRD_OTA_HEADROOM_MARGIN_SIZE \
"

FILES:${PN} = " \
    ${libexecdir}/superbird-provision \
    ${systemd_system_unitdir}/superbird-provision.service \
"
