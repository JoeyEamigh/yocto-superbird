SUMMARY = "Bridgething hub launcher webapp"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "${AUTOREV}"

DEPENDS = "bun-native"

inherit allarch

WEBAPP_DIR = "${nonarch_libdir}/bridgething/webapps/hub"

do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    # install at the monorepo root so workspace:* resolves, then build hub only.
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

FILES:${PN} = "${WEBAPP_DIR}"
