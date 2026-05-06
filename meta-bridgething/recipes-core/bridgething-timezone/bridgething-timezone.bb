SUMMARY = "Writable timezone state for the read-only Bridgething rootfs"
DESCRIPTION = "Carries /etc/localtime and /etc/timezone off the read-only \
squashfs onto /var/lib/timezone (settings partition, persistent across \
reboots and OTAs). Ships:\
  - /etc/localtime, /etc/timezone as relative symlinks into \
    /var/lib/timezone/, replacing the regular files tzdata installs;\
  - a systemd-tmpfiles snippet that creates the directory and a UTC \
    default on first boot, idempotent on every boot after that;\
  - a systemd-timedated.service drop-in that points the daemon at the \
    writable path via SYSTEMD_ETC_LOCALTIME and grants it write access \
    via an extra ReadWritePaths entry.\
\
Without this, timedatectl set-timezone fails with EROFS because \
systemd-timedated tries to atomically replace /etc/localtime and /etc \
is mounted read-only."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-timezone.conf \
    file://timezone-state.conf \
"

S = "${UNPACKDIR}"

# tzdata owns the actual zoneinfo database that /var/lib/timezone/localtime
# eventually points at; without it timedatectl set-timezone can succeed
# but glibc resolves to UTC.
RDEPENDS:${PN} = "tzdata"

# Replaces /etc/localtime + /etc/timezone, both originally provided by
# tzdata. The file collision is resolved by stripping those paths from
# the tzdata package via a sibling bbappend.
do_install() {
    install -d ${D}${sysconfdir}
    ln -s ../var/lib/timezone/localtime ${D}${sysconfdir}/localtime
    ln -s ../var/lib/timezone/timezone  ${D}${sysconfdir}/timezone

    install -d ${D}${libdir}/tmpfiles.d
    install -m 0644 ${S}/bridgething-timezone.conf \
        ${D}${libdir}/tmpfiles.d/bridgething-timezone.conf

    install -d ${D}${systemd_system_unitdir}/systemd-timedated.service.d
    install -m 0644 ${S}/timezone-state.conf \
        ${D}${systemd_system_unitdir}/systemd-timedated.service.d/timezone-state.conf
}

FILES:${PN} = " \
    ${sysconfdir}/localtime \
    ${sysconfdir}/timezone \
    ${libdir}/tmpfiles.d/bridgething-timezone.conf \
    ${systemd_system_unitdir}/systemd-timedated.service.d/timezone-state.conf \
"
