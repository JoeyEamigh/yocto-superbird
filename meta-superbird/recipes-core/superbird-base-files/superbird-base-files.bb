SUMMARY = "Superbird system tuning + base config files"
DESCRIPTION = "BSP-level system config: zRAM swap tuning, scroll-wheel \
input rules, the libubootenv fw_env.config, and the /var/cache tmpfiles \
seed. /etc/fstab lives in a base-files bbappend (sibling \
recipes-core/base-files/) since base-files owns that path. Partition \
naming needs no rule here - udev's stock 60-persistent-storage.rules \
already exposes every GPT partition under /dev/disk/by-partlabel/."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://zram.conf \
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
    install -m 0644 ${S}/61-bridgething-rotary.rules \
        ${D}${sysconfdir}/udev/rules.d/61-bridgething-rotary.rules

    install -d ${D}${sysconfdir}/libinput
    install -m 0644 ${S}/50-bridgething-rotary.quirks \
        ${D}${sysconfdir}/libinput/local-overrides.quirks

    install -m 0644 ${S}/fw_env.config ${D}${sysconfdir}/fw_env.config
    install -m 0644 ${S}/hwrevision    ${D}${sysconfdir}/hwrevision

    # Mount point for the u-boot env partition (fstab: PARTLABEL=env ->
    # /mnt/uboot-env). The rootfs is read-only squashfs, so the directory
    # must be baked in at build time - systemd cannot create a mount
    # point under /mnt when the parent filesystem is read-only.
    install -d ${D}/mnt/uboot-env

    # Ensure /var/cache exists with sane perms on first boot. /var is on
    # the writable data partition; without an explicit tmpfile entry,
    # nothing creates this until something writes to it.
    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${S}/superbird-cache.conf \
        ${D}${libdir}/tmpfiles.d/superbird-cache.conf

    # Mesa's shader cache, libxkbcommon's compose cache, fontconfig's
    # cache - everything XDG-spec-respecting writes to ~/.cache. Root
    # user's home is /root, which sits on the read-only rootfs, so the
    # default location fails silently and forces re-compilation on every
    # boot. Redirect to /var/cache (writable, persistent across reboots
    # but wiped on factory reset since it's on the data partition).
    install -d ${D}/root
    ln -sf /var/cache ${D}/root/.cache
}

FILES:${PN} = " \
    ${sysconfdir}/sysctl.d/30-zram.conf \
    ${sysconfdir}/udev/rules.d/61-bridgething-rotary.rules \
    ${sysconfdir}/libinput/local-overrides.quirks \
    ${sysconfdir}/fw_env.config \
    ${sysconfdir}/hwrevision \
    ${libdir}/tmpfiles.d/superbird-cache.conf \
    /root/.cache \
    /mnt/uboot-env \
"
