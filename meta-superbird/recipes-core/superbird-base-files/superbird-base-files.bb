SUMMARY = "Superbird system tuning + base config files"
DESCRIPTION = "zRAM tuning and udev rules mapping the stock Amlogic partition numbers to friendly /dev/<role> aliases."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://zram.conf \
    file://10-superbird-partitions.rules \
    file://61-bridgething-rotary.rules \
    file://50-bridgething-rotary.quirks \
    file://fw_env.config \
    file://hwrevision \
    file://superbird-cache.conf \
"

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sysconfdir}/sysctl.d
    install -m 0644 ${S}/zram.conf ${D}${sysconfdir}/sysctl.d/30-zram.conf

    install -d ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${S}/10-superbird-partitions.rules \
        ${D}${sysconfdir}/udev/rules.d/10-superbird-partitions.rules
    install -m 0644 ${S}/61-bridgething-rotary.rules \
        ${D}${sysconfdir}/udev/rules.d/61-bridgething-rotary.rules

    install -d ${D}${sysconfdir}/libinput
    install -m 0644 ${S}/50-bridgething-rotary.quirks \
        ${D}${sysconfdir}/libinput/local-overrides.quirks

    install -m 0644 ${S}/fw_env.config ${D}${sysconfdir}/fw_env.config
    install -m 0644 ${S}/hwrevision    ${D}${sysconfdir}/hwrevision

    # seed /var/cache so XDG caches land on the writable data partition
    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${S}/superbird-cache.conf \
        ${D}${libdir}/tmpfiles.d/superbird-cache.conf

    # /root is on the ro rootfs; redirect ~/.cache to the writable /var/cache
    install -d ${D}/root
    ln -sf /var/cache ${D}/root/.cache
}

FILES:${PN} = " \
    ${sysconfdir}/sysctl.d/30-zram.conf \
    ${sysconfdir}/udev/rules.d/10-superbird-partitions.rules \
    ${sysconfdir}/udev/rules.d/61-bridgething-rotary.rules \
    ${sysconfdir}/libinput/local-overrides.quirks \
    ${sysconfdir}/fw_env.config \
    ${sysconfdir}/hwrevision \
    ${libdir}/tmpfiles.d/superbird-cache.conf \
    /root/.cache \
"
