# Drop the meta-oe/wrynose patches that adapt neatvnc 0.9.5 to aml 1.x.
# We pin aml back to v0.3.0 in our own layer (see
# meta-bridgething/recipes-graphics/aml/aml_0.3.0.bb) because poky/wrynose's
# weston 15.0.0 has a hard `dependency('aml', version: ['>= 0.3.0', '< 0.4.0'])`
# floor in libweston/backend-vnc/meson.build - and neither weston upstream
# main nor poky/wrynose has bumped the range yet. With aml 0.3.0 everywhere,
# vanilla neatvnc 0.9.5 looks for the right pkg-config name (`aml.pc`) and
# version, so the meta-oe aml1 retrofit becomes wrong.
#
# Drop these along with the bridgething.conf PREFERRED_VERSION_aml override
# the moment weston widens its aml range to accept 1.x.
SRC_URI:remove = " \
    file://0001-meson-Use-new-pkgconfig-for-aml1.patch \
    file://0001-Use-aml-v1.patch \
"
