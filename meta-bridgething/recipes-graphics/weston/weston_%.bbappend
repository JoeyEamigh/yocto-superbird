FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Split the VNC backend module into its own package so the dev image
# can opt in (via packagegroup-bridgething-dev RDEPENDS) without prod
# silently shipping a VNC code path it doesn't want. Weston still
# builds with the vnc PACKAGECONFIG enabled (set in the distro conf
# so the .so is available for dev) but prod's weston package no
# longer contains vnc-backend.so - the .so files for the backends
# normally live in ${libdir}/libweston-${WESTON_MAJOR_VERSION}/, which
# poky's weston recipe lumps into the libweston-${WESTON_MAJOR_VERSION}
# package via a "*.so" glob; PACKAGES =+ here puts our specific
# weston-vnc-backend package earlier in the assignment order so it
# claims vnc-backend.so first.

PACKAGES =+ "${PN}-vnc-backend"

# Co-package weston-remote-access PAM with vnc-backend.so: weston-vnc
# has no native "no auth" mode and always routes VNC client creds
# through the weston-remote-access PAM service, which upstream wires
# to `login` (real system password). Replace it with pam_permit
# (accepts any creds) so VNC is a one-click debug surface on the dev
# image. Listing the path under ${PN}-vnc-backend's FILES makes our
# split package claim it before the main weston package's broad
# ${sysconfdir} glob.
FILES:${PN}-vnc-backend = "${libdir}/libweston-${WESTON_MAJOR_VERSION}/vnc-backend.so \
                           ${sysconfdir}/pam.d/weston-remote-access"

# Move VNC-specific runtime deps off of ${PN} (where poky's
# PACKAGECONFIG[vnc] auto-adds them) and onto our split package, so
# prod's weston install doesn't pull neatvnc + libaml.
RDEPENDS:${PN}-vnc-backend = "neatvnc aml libpam"

# Build-time dep for the VNC backend's libaml include - poky's
# PACKAGECONFIG[vnc] only declares `neatvnc libpam` and misses libaml,
# which weston-15's backend-vnc/meson.build requires. aml ships in
# meta-oe.
DEPENDS:append = " aml"

# Replace upstream's weston-remote-access PAM service with the
# pam_permit variant. Runs after weston's own meson install puts the
# upstream version in place.
SRC_URI:append = " file://weston-remote-access.pam"

# Drop the upstream vnc_handle_auth getpwnam precondition. Without
# this patch, even with pam_permit configured, the VNC username has
# to map to a real local account whose uid matches weston's - forcing
# "root" + any password every connect. With the patch, PAM (which is
# pam_permit on the dev image) is the sole gate.
SRC_URI:append = " file://0001-backend-vnc-skip-getpwnam-guard-in-vnc_handle_auth.patch"

do_install:append() {
    install -m 0644 ${UNPACKDIR}/weston-remote-access.pam \
        ${D}${sysconfdir}/pam.d/weston-remote-access
}
