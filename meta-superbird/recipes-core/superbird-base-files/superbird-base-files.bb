SUMMARY = "Superbird system tuning + base config files"
DESCRIPTION = "zRAM tuning, rotary input rules, libubootenv fw_env config, /var/cache tmpfiles seed."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://zram.conf \
    file://61-superbird-rotary.rules \
    file://50-superbird-rotary.quirks \
    file://fw_env.config \
    file://hwrevision \
    file://superbird-cache.conf \
    file://05-ro-cachedir.conf \
"

S = "${UNPACKDIR}"

do_install() {
    install -d ${D}${sysconfdir}/sysctl.d
    install -m 0644 ${S}/zram.conf ${D}${sysconfdir}/sysctl.d/30-zram.conf

    install -d ${D}${sysconfdir}/udev/rules.d
    install -m 0644 ${S}/61-superbird-rotary.rules \
        ${D}${sysconfdir}/udev/rules.d/61-superbird-rotary.rules

    install -d ${D}${sysconfdir}/libinput
    install -m 0644 ${S}/50-superbird-rotary.quirks \
        ${D}${sysconfdir}/libinput/local-overrides.quirks

    install -m 0644 ${S}/fw_env.config ${D}${sysconfdir}/fw_env.config
    install -m 0644 ${S}/hwrevision    ${D}${sysconfdir}/hwrevision

    # mount point for env partlabel; rootfs is ro squashfs so the dir is baked at build time.
    install -d ${D}/mnt/uboot-env

    # seed /var/cache on the writable data partition.
    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${S}/superbird-cache.conf \
        ${D}${libdir}/tmpfiles.d/superbird-cache.conf

    # /root is on the ro rootfs; redirect ~/.cache to the writable /var/cache
    install -d ${D}/root
    ln -sf /var/cache ${D}/root/.cache

    # ro font cache dir baked at rootfs time; read before the /var cachedir
    install -d ${D}${sysconfdir}/fonts/conf.d
    install -m 0644 ${S}/05-ro-cachedir.conf \
        ${D}${sysconfdir}/fonts/conf.d/05-ro-cachedir.conf
    install -d ${D}${datadir}/fontconfig/cache
}

FILES:${PN} = " \
    ${sysconfdir}/sysctl.d/30-zram.conf \
    ${sysconfdir}/udev/rules.d/61-superbird-rotary.rules \
    ${sysconfdir}/libinput/local-overrides.quirks \
    ${sysconfdir}/fw_env.config \
    ${sysconfdir}/hwrevision \
    ${libdir}/tmpfiles.d/superbird-cache.conf \
    ${sysconfdir}/fonts/conf.d/05-ro-cachedir.conf \
    ${datadir}/fontconfig/cache \
    /root/.cache \
    /mnt/uboot-env \
"
