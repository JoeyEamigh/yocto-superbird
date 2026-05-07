SUMMARY = "Bridgething first-boot init: /etc/superbird metadata + BT alias"
DESCRIPTION = "Ships the meta.json template consumed by the \
bridgething daemon (read at /etc/superbird, which symlinks into \
/var/lib/superbird/meta.json on the settings partition), plus a \
oneshot systemd unit that fills in efuse-derived fields (btMac, \
serialNumber) on first boot and seeds the bluez alias so the \
device advertises as 'Car Thing (SN: xxxx)'. \
\
Static fields (name, version, fccId, icId, modelName, image \
build identity) are baked in at build time via bitbake variable \
expansion against the template."
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

# superbird-init.sh uses only POSIX shell builtins + hexdump + sed +
# grep + busybox-awk-equivalent utilities. The full gawk binary (and
# its libmpfr.so dep, ~4 MB combined) was historically pulled here as
# a "just in case" bound; the script never invokes any gawk-specific
# extension. Removing gawk drops mpfr from the rootfs by the same
# transitive trim.
RDEPENDS:${PN} = ""

# Bake the build-time identity. DATETIME is bitbake's per-build
# timestamp; we render it as both a tag (build_id) and ISO-8601
# date for human-friendly display in the gateway UI.
IMAGE_BUILD_ID = "${DISTRO}-${DISTRO_VERSION}-${DATETIME}"
IMAGE_BUILD_DATE = "${@d.getVar('DATETIME')[0:4]}-${@d.getVar('DATETIME')[4:6]}-${@d.getVar('DATETIME')[6:8]}T${@d.getVar('DATETIME')[8:10]}:${@d.getVar('DATETIME')[10:12]}:${@d.getVar('DATETIME')[12:14]}Z"
IMAGE_BUILD_ID[vardepsexclude] = "DATETIME"
IMAGE_BUILD_DATE[vardepsexclude] = "DATETIME"

# The @BRIDGETHING_CHANNEL@ / @BRIDGETHING_IMAGE_VARIANT@ /
# @BRIDGETHING_IMAGE_VERSION@ placeholders in the template are filled
# in at image-build time (IMAGE_PREPROCESS_COMMAND in
# bridgething-image-base.inc) because they're per-image, not
# per-package. The package-level recipe leaves them as literal
# placeholders for the image stage to replace.

do_install() {
    install -d ${D}${datadir}/superbird

    # Render the meta.json template. Each @VAR@ placeholder maps to
    # a bitbake variable. Done with sed (not file://...;subdir=...)
    # because we need the values fresh at do_install time, not at
    # SRC_URI fetch time.
    sed \
        -e "s|@DISTRO_NAME@|${DISTRO_NAME}|g" \
        -e "s|@DISTRO_VERSION@|${DISTRO_VERSION}|g" \
        -e "s|@DISTRO@|${DISTRO}|g" \
        -e "s|@MACHINE@|${MACHINE}|g" \
        -e "s|@SUPERBIRD_DESCRIPTION@|${SUPERBIRD_DESCRIPTION}|g" \
        -e "s|@SUPERBIRD_FCC_ID@|${SUPERBIRD_FCC_ID}|g" \
        -e "s|@SUPERBIRD_IC_ID@|${SUPERBIRD_IC_ID}|g" \
        -e "s|@SUPERBIRD_MODEL_NAME@|${SUPERBIRD_MODEL_NAME}|g" \
        -e "s|@IMAGE_BUILD_ID@|${IMAGE_BUILD_ID}|g" \
        -e "s|@IMAGE_BUILD_DATE@|${IMAGE_BUILD_DATE}|g" \
        ${S}/superbird-meta.json.in \
        > ${D}${datadir}/superbird/meta.json.in

    # Init script runs once per boot, populates the efuse fields.
    install -d ${D}${libexecdir}
    install -m 0755 ${S}/superbird-init.sh ${D}${libexecdir}/superbird-init

    # Systemd unit.
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/superbird-init.service \
        ${D}${systemd_system_unitdir}/superbird-init.service

    # /etc/superbird → /var/lib/superbird/meta.json. The init
    # script materializes /var/lib/superbird/meta.json from the
    # template on first boot.
    install -d ${D}${sysconfdir}
    ln -s ../var/lib/superbird/meta.json ${D}${sysconfdir}/superbird

    # SSH host-key plumbing. /etc/ssh is on the read-only system
    # partition, so openssh's sshd_check_keys can't write keys
    # there. Two-part fix:
    #   1. /etc/default/ssh sets SYSCONFDIR=/var/lib/ssh, which
    #      sshd_check_keys uses as the keygen target.
    #   2. /etc/ssh/ssh_host_*_key{,.pub} symlinks point at the
    #      writable /var/lib/ssh/ copies, so the default
    #      sshd_config (which references /etc/ssh/ssh_host_*_key)
    #      transparently follows through to the real keys.
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
