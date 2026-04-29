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

FILES:${PN}-vnc-backend = "${libdir}/libweston-${WESTON_MAJOR_VERSION}/vnc-backend.so"

# Move VNC-specific runtime deps off of ${PN} (where poky's
# PACKAGECONFIG[vnc] auto-adds them) and onto our split package, so
# prod's weston install doesn't pull neatvnc + libaml.
RDEPENDS:${PN}-vnc-backend = "neatvnc aml libpam"

# Build-time dep for the VNC backend's libaml include - poky's
# PACKAGECONFIG[vnc] only declares `neatvnc libpam` and misses libaml,
# which weston-15's backend-vnc/meson.build requires. aml ships in
# meta-oe.
DEPENDS:append = " aml"
