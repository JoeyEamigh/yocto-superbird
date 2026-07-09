SUMMARY = "Stock Spotify webapp for bridgething"
HOMEPAGE = "https://github.com/thinglabsoss/superbird-webapp"
LICENSE = "CLOSED"

SRC_URI = "git://github.com/thinglabsoss/superbird-webapp.git;protocol=https;branch=bridgething \
           file://spotify.svg \
           file://manifest.json"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native"

inherit allarch

WEBAPP_DIR = "${nonarch_libdir}/bridgething/webapps/stock"

do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    bun install --frozen-lockfile --no-progress
    bun run build
}

do_install() {
    install -d ${D}${WEBAPP_DIR}
    cp -r ${S}/dist/. ${D}${WEBAPP_DIR}/
    install -m 0644 ${UNPACKDIR}/spotify.svg ${D}${WEBAPP_DIR}/spotify.svg
    install -m 0644 ${UNPACKDIR}/manifest.json ${D}${WEBAPP_DIR}/manifest.json
    chown -R root:root ${D}${WEBAPP_DIR}
    find ${D}${WEBAPP_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${WEBAPP_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${WEBAPP_DIR}"
