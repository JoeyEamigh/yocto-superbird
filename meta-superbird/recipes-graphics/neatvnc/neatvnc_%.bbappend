# drop meta-oe's aml1 retrofit; we pin aml back to v0.3.0 to satisfy weston-15's range
SRC_URI:remove = " \
    file://0001-meson-Use-new-pkgconfig-for-aml1.patch \
    file://0001-Use-aml-v1.patch \
"

# tls must be linked even when weston-vnc runs with --disable-tls or the auth setup bails
PACKAGECONFIG:append = " tls"
