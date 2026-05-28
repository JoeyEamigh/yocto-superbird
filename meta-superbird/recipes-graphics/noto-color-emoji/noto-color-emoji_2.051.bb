SUMMARY = "Noto Color Emoji"
HOMEPAGE = "https://github.com/googlefonts/noto-emoji"
SECTION = "fonts"

LICENSE = "OFL-1.1"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/OFL-1.1;md5=fac3a519e5e9eb96316656e0ca4f2b90"

SRCREV = "8998f5dd683424a73e2314a8c1f1e359c19e8742"
SRC_URI = " \
    https://raw.githubusercontent.com/googlefonts/noto-emoji/${SRCREV}/fonts/Noto-COLRv1.ttf;downloadfilename=Noto-COLRv1-${PV}.ttf \
    file://75-noto-color-emoji.conf \
"
SRC_URI[sha256sum] = "0ae57fe58645638523ba35f388d93739d292539a9acb84df5700c81b1e1a28d2"

S = "${UNPACKDIR}"

# data-only
INHIBIT_DEFAULT_DEPS = "1"
inherit allarch fontcache

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${datadir}/fonts/truetype
    install -m 0644 ${UNPACKDIR}/Noto-COLRv1-${PV}.ttf \
        ${D}${datadir}/fonts/truetype/NotoColorEmoji-COLRv1.ttf

    install -d ${D}${sysconfdir}/fonts/conf.d
    install -m 0644 ${UNPACKDIR}/75-noto-color-emoji.conf \
        ${D}${sysconfdir}/fonts/conf.d/75-noto-color-emoji.conf
}

FILES:${PN} = " \
    ${datadir}/fonts/truetype/NotoColorEmoji-COLRv1.ttf \
    ${sysconfdir}/fonts/conf.d/75-noto-color-emoji.conf \
"
