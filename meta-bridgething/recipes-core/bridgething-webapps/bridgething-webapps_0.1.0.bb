SUMMARY = "Bridgething hub launcher webapp + example webapps"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native zip-native"

inherit allarch

# Hub launcher is served live; example webapps are zipped and seeded into
# /var/bridgething/webapps on first boot by the daemon. Both ship from one
# recipe so the monorepo `bun install` runs exactly once: building hub and
# examples as separate recipes means each clones the repo and installs the
# whole workspace redundantly, and under EXTERNALSRC (bridgething-local) they
# share one source tree and race on node_modules.
WEBAPP_DIR = "${nonarch_libdir}/bridgething/webapps/hub"
EXAMPLES_DIR = "${nonarch_libdir}/bridgething/examples"
EXAMPLE_WEBAPPS = "weather calendar"

do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    # install once at the monorepo root so workspace:* resolves, then build all.
    bun install --frozen-lockfile --no-progress
    bun run build --filter=@bridgething/hub-webapp
    for app in ${EXAMPLE_WEBAPPS}; do
        bun run build --filter=@bridgething/example-${app}
    done
}

do_install() {
    install -d ${D}${WEBAPP_DIR}
    cp -r ${S}/packages/hub-webapp/dist/. ${D}${WEBAPP_DIR}/

    install -d ${D}${EXAMPLES_DIR}
    for app in ${EXAMPLE_WEBAPPS}; do
        dist="${S}/packages/examples/${app}/dist"
        if [ ! -f "${dist}/manifest.json" ]; then
            bbfatal "example ${app}: ${dist}/manifest.json missing after build"
        fi
        ( cd "${dist}" && zip -r -X -q "${D}${EXAMPLES_DIR}/${app}.zip" . )
    done

    chown -R root:root ${D}${WEBAPP_DIR} ${D}${EXAMPLES_DIR}
    find ${D}${WEBAPP_DIR} ${D}${EXAMPLES_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${WEBAPP_DIR} ${D}${EXAMPLES_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${WEBAPP_DIR} ${EXAMPLES_DIR}"
