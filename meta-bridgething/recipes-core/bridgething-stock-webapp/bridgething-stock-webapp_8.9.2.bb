SUMMARY = "Stock Spotify webapp as a built-in bridgething webapp"
DESCRIPTION = "Builds the thinglabs stock webapp with bun and stages it at /usr/share/bridgething/webapps/stock/."
HOMEPAGE = "https://github.com/thinglabsoss/superbird-webapp"
LICENSE = "CLOSED"

SRC_URI = "git://github.com/thinglabsoss/superbird-webapp.git;protocol=https;branch=bridgething \
           file://spotify.svg"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native"

inherit allarch

WEBAPP_DIR = "${datadir}/bridgething/webapps/stock"

# bun install hits the npm registry
do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    bun install --frozen-lockfile --no-progress
    bun pm trust --all
    bun run build
}

do_install() {
    install -d ${D}${WEBAPP_DIR}
    cp -r ${S}/dist/. ${D}${WEBAPP_DIR}/
    install -m 0644 ${UNPACKDIR}/spotify.svg ${D}${WEBAPP_DIR}/spotify.svg
    chown -R root:root ${D}${WEBAPP_DIR}
    find ${D}${WEBAPP_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${WEBAPP_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${datadir}/bridgething/webapps/stock"
