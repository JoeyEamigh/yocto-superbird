SUMMARY = "Superbird first-boot init"
DESCRIPTION = "Renders /var/lib/superbird/meta.json from a template each boot (patching in efused btMac + serialNumber) and seeds the bluez alias. Also redirects sshd's keygen target so ssh keys persist across the ro rootfs."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://superbird-meta.json.in \
    file://superbird-init.sh \
    file://superbird-init.service \
    file://default-ssh \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "superbird-init.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = ""

# distro / machine scope literals substitute here; per-image fields (imageBuildId,
# imageBuildDate, plus anything downstream layers inject) substitute via the
# superbird-image bbclass at IMAGE_PREPROCESS time.
do_install() {
    install -d ${D}${datadir}/superbird

    sed \
        -e "s|@DISTRO_NAME@|${DISTRO_NAME}|g" \
        -e "s|@DISTRO_VERSION@|${DISTRO_VERSION}|g" \
        -e "s|@DISTRO@|${DISTRO}|g" \
        -e "s|@MACHINE@|${MACHINE}|g" \
        -e "s|@SUPERBIRD_DESCRIPTION@|${SUPERBIRD_DESCRIPTION}|g" \
        -e "s|@SUPERBIRD_FCC_ID@|${SUPERBIRD_FCC_ID}|g" \
        -e "s|@SUPERBIRD_IC_ID@|${SUPERBIRD_IC_ID}|g" \
        -e "s|@SUPERBIRD_MODEL_NAME@|${SUPERBIRD_MODEL_NAME}|g" \
        ${S}/superbird-meta.json.in \
        > ${D}${datadir}/superbird/meta.json.in

    install -d ${D}${libexecdir}
    install -m 0755 ${S}/superbird-init.sh ${D}${libexecdir}/superbird-init

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-init.service \
        ${D}${systemd_system_unitdir}/superbird-init.service

    install -d ${D}${sysconfdir}
    ln -s ../var/lib/superbird/meta.json ${D}${sysconfdir}/superbird

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${S}/default-ssh ${D}${sysconfdir}/default/ssh

    install -d ${D}${sysconfdir}/ssh
    for k in rsa ecdsa ed25519; do
        ln -s ../../var/lib/ssh/ssh_host_${k}_key \
            ${D}${sysconfdir}/ssh/ssh_host_${k}_key
        ln -s ../../var/lib/ssh/ssh_host_${k}_key.pub \
            ${D}${sysconfdir}/ssh/ssh_host_${k}_key.pub
    done
}

FILES:${PN} = " \
    ${datadir}/superbird/meta.json.in \
    ${libexecdir}/superbird-init \
    ${systemd_system_unitdir}/superbird-init.service \
    ${sysconfdir}/superbird \
    ${sysconfdir}/default/ssh \
    ${sysconfdir}/ssh/ssh_host_rsa_key \
    ${sysconfdir}/ssh/ssh_host_rsa_key.pub \
    ${sysconfdir}/ssh/ssh_host_ecdsa_key \
    ${sysconfdir}/ssh/ssh_host_ecdsa_key.pub \
    ${sysconfdir}/ssh/ssh_host_ed25519_key \
    ${sysconfdir}/ssh/ssh_host_ed25519_key.pub \
"
