SUMMARY = "Bridgething builtin webapps + example webapps"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native zip-native"

inherit allarch

BUILTIN_DIR = "${nonarch_libdir}/bridgething/webapps"
EXAMPLES_DIR = "${nonarch_libdir}/bridgething/examples"
BUILTIN_WEBAPPS = "hub browser"
EXAMPLE_WEBAPPS = "weather calendar home-assistant"

do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    # install once at the monorepo root so workspace:* resolves, then build all.
    bun install --frozen-lockfile --no-progress
    for app in ${BUILTIN_WEBAPPS}; do
        bun run build --filter=@bridgething/${app}-webapp
    done
    for app in ${EXAMPLE_WEBAPPS}; do
        bun run build --filter=@bridgething/example-${app}
    done
}

do_install() {
    install -d ${D}${BUILTIN_DIR}
    for app in ${BUILTIN_WEBAPPS}; do
        dist="${S}/packages/${app}-webapp/dist"
        if [ ! -f "${dist}/manifest.json" ]; then
            bbfatal "builtin ${app}: ${dist}/manifest.json missing after build"
        fi
        install -d ${D}${BUILTIN_DIR}/${app}
        cp -r "${dist}/." ${D}${BUILTIN_DIR}/${app}/
    done

    install -d ${D}${EXAMPLES_DIR}
    for app in ${EXAMPLE_WEBAPPS}; do
        dist="${S}/packages/examples/${app}/dist"
        if [ ! -f "${dist}/manifest.json" ]; then
            bbfatal "example ${app}: ${dist}/manifest.json missing after build"
        fi
        ( cd "${dist}" && zip -r -X -q "${D}${EXAMPLES_DIR}/${app}.zip" . )
    done

    chown -R root:root ${D}${BUILTIN_DIR} ${D}${EXAMPLES_DIR}
    find ${D}${BUILTIN_DIR} ${D}${EXAMPLES_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${BUILTIN_DIR} ${D}${EXAMPLES_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${BUILTIN_DIR} ${EXAMPLES_DIR}"
