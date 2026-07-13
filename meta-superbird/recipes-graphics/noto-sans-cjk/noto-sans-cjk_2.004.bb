SUMMARY = "Noto Sans CJK variable font collection"
HOMEPAGE = "https://github.com/notofonts/noto-cjk"
SECTION = "fonts"

LICENSE = "OFL-1.1"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/OFL-1.1;md5=fac3a519e5e9eb96316656e0ca4f2b90"

SRCREV = "523d033d6cb47f4a80c58a35753646f5c3608a78"
SRC_URI = " \
    https://raw.githubusercontent.com/notofonts/noto-cjk/${SRCREV}/Sans/Variable/OTC/NotoSansCJK-VF.otf.ttc;downloadfilename=NotoSansCJK-VF-${PV}.otf.ttc \
    file://76-noto-sans-cjk.conf \
"
SRC_URI[sha256sum] = "d3d8256cdec8dbcb3552284bc6b20c734dd60c2ee9df83b5758e34807c4bac32"

S = "${UNPACKDIR}"

# data-only
INHIBIT_DEFAULT_DEPS = "1"
inherit allarch fontcache

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${datadir}/fonts/opentype
    install -m 0644 ${UNPACKDIR}/NotoSansCJK-VF-${PV}.otf.ttc \
        ${D}${datadir}/fonts/opentype/NotoSansCJK-VF.otf.ttc

    install -d ${D}${sysconfdir}/fonts/conf.d
    install -m 0644 ${UNPACKDIR}/76-noto-sans-cjk.conf \
        ${D}${sysconfdir}/fonts/conf.d/76-noto-sans-cjk.conf
}

FILES:${PN} = " \
    ${datadir}/fonts/opentype/NotoSansCJK-VF.otf.ttc \
    ${sysconfdir}/fonts/conf.d/76-noto-sans-cjk.conf \
"
