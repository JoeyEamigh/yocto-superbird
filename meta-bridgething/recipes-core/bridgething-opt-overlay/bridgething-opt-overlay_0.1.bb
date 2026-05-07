SUMMARY = "Persistent /opt/bridgething overlay backed by the settings partition"
DESCRIPTION = "Oneshot systemd service that mkdir's /var/lib/bridgething/persist \
on the settings partition (shared between system_a and system_b) and \
bind-mounts it onto /opt/bridgething. The bind-mount is the writable home \
of the daemon binary (/opt/bridgething/daemon/bridgething.current), \
installed webapps (/opt/bridgething/webapps/), and any artifacts pushed \
during dev iteration. Survives bootslot swaps and OTA upgrades. \
\
The bind target also receives the pre-populated settings.ext4 contents \
shipped in the flashable bundle, so first-flash devices come up with the \
current daemon binary already in place. \
\
Implemented as a oneshot rather than a .mount unit because mount units \
that order After=systemd-tmpfiles-setup hit an ordering cycle (mount is \
WantedBy=local-fs.target which already requires tmpfiles, and adding the \
After produces a cycle that systemd silently breaks by dropping tmpfiles \
from the chain - leaving the mount inactive and /var/lib/bridgething/* \
uncreated). The oneshot does mkdir + mount itself, ordered After= \
local-fs.target so /var/lib is mounted but with no Before-target trap."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://bridgething-opt-overlay.service \
"
S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "bridgething-opt-overlay.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/bridgething-opt-overlay.service \
        ${D}${systemd_system_unitdir}/bridgething-opt-overlay.service

    # /opt/bridgething has to exist in the read-only rootfs as the
    # bind-mount target - mount(8) needs the directory to exist before
    # it can mount onto it, and tmpfiles.d can't create dirs under
    # /opt at boot (rootfs is ro). Baking the empty dir into the image
    # puts the mountpoint in place at flash time.
    install -d ${D}/opt/bridgething
}

FILES:${PN} = " \
    ${systemd_system_unitdir}/bridgething-opt-overlay.service \
    /opt \
    /opt/bridgething \
"
