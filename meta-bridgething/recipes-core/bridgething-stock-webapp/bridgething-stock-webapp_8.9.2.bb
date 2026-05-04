SUMMARY = "Spotify Car Thing stock webapp, served as a built-in bridgething webapp"
DESCRIPTION = "Builds the thinglabs-maintained stock Spotify webapp from \
source using bun, then stages the produced dist tree at \
/usr/share/bridgething/webapps/stock/. The bridgething daemon discovers it \
as a built-in webapp (read-only, cannot be uninstalled) and uses 'stock' as \
the boot-default active app."
HOMEPAGE = "https://github.com/thinglabsoss/superbird-webapp"
LICENSE = "CLOSED"

SRC_URI = "git://github.com/thinglabsoss/superbird-webapp.git;protocol=https;branch=thinglabs"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native"

# Pure data package once built: produced dist is plain HTML/JS/CSS.
inherit allarch

WEBAPP_DIR = "${datadir}/bridgething/webapps/stock"

# bun install hits the npm registry; allow the fetch task to use the network.
do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    # --frozen-lockfile pins to the committed bun.lock; SRCREV pins the
    # lockfile itself, so the resolved tree is deterministic for a given
    # recipe revision.
    bun install --frozen-lockfile --no-progress
    bun pm trust --all
    bun run build
}

do_install() {
    install -d ${D}${WEBAPP_DIR}
    cp -r ${S}/dist/. ${D}${WEBAPP_DIR}/
    chown -R root:root ${D}${WEBAPP_DIR}
    find ${D}${WEBAPP_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${WEBAPP_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${datadir}/bridgething/webapps/stock"
