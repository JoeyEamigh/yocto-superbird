SUMMARY = "Bridgething example webapps, zipped for first-boot seeding"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native zip-native"

inherit allarch

# rebased to /opt/bridgething/examples/ inside the bandaid; the daemon
# seeds every *.zip here into /var/bridgething/webapps once on first boot.
EXAMPLES_DIR = "${nonarch_libdir}/bridgething/examples"

EXAMPLE_WEBAPPS = "weather calendar"

do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    # install at the monorepo root so workspace:* resolves, then build the examples.
    bun install --frozen-lockfile --no-progress
    for app in ${EXAMPLE_WEBAPPS}; do
        bun run build --filter=@bridgething/example-${app}
    done
}

do_install() {
    install -d ${D}${EXAMPLES_DIR}
    for app in ${EXAMPLE_WEBAPPS}; do
        dist="${S}/packages/examples/${app}/dist"
        if [ ! -f "${dist}/manifest.json" ]; then
            bbfatal "example ${app}: ${dist}/manifest.json missing after build"
        fi
        ( cd "${dist}" && zip -r -X -q "${D}${EXAMPLES_DIR}/${app}.zip" . )
    done
    chown -R root:root ${D}${EXAMPLES_DIR}
    find ${D}${EXAMPLES_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${EXAMPLES_DIR}"
