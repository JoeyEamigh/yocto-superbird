SUMMARY = "Bridgething hub launcher webapp, served as a built-in bridgething webapp"
DESCRIPTION = "Builds the bridgething launcher webapp (packages/hub-webapp \
in the bridgething monorepo) from source using bun, then stages the produced \
dist tree at /usr/share/bridgething/webapps/hub/. The bridgething daemon \
discovers it as a built-in webapp (read-only, cannot be uninstalled)."
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native"

# Pure data package once built: produced dist is plain HTML/JS/CSS.
inherit allarch

WEBAPP_DIR = "${datadir}/bridgething/webapps/hub"

# bun install hits the npm registry; allow the fetch task to use the network.
do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    # Run install at the monorepo root so workspace:* resolution works,
    # then build only the hub-webapp filter.
    bun install --frozen-lockfile --no-progress
    bun run build --filter=@bridgething/hub-webapp
}

do_install() {
    install -d ${D}${WEBAPP_DIR}
    cp -r ${S}/packages/hub-webapp/dist/. ${D}${WEBAPP_DIR}/
    chown -R root:root ${D}${WEBAPP_DIR}
    find ${D}${WEBAPP_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${WEBAPP_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${datadir}/bridgething/webapps/hub"
