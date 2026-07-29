SUMMARY = "Bridgething builtin webapps + example webapps"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require bridgething-webapps-pin.inc

BRIDGETHING_WEBAPPS_PROVISION ?= "prebuilt"

WEBAPPS_GIT_URI = "git://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"

FROM_SOURCE = "${@'1' if d.getVar('BRIDGETHING_WEBAPPS_PROVISION') == 'source' else '0'}"

SRC_URI = "${@d.getVar('WEBAPPS_GIT_URI') if d.getVar('FROM_SOURCE') == '1' else d.getVar('BRIDGETHING_WEBAPPS_SRC_URI')}"
SRCREV = "${AUTOREV}"

DEPENDS = "${@'bun-native zip-native' if d.getVar('FROM_SOURCE') == '1' else 'unzip-native'}"

inherit allarch

BUILTIN_DIR = "${nonarch_libdir}/bridgething/webapps"
EXAMPLES_DIR = "${nonarch_libdir}/bridgething/examples"
BUILTIN_WEBAPPS = "hub browser"
EXAMPLE_WEBAPPS = "weather calendar home-assistant"

do_compile[network] = "1"
BUN_HOME = "${WORKDIR}/bun-home"

python () {
    if d.getVar('FROM_SOURCE') == '1':
        return
    d.delVar('SRCREV')
    for task in ('do_configure', 'do_compile'):
        d.setVarFlag(task, 'noexec', '1')
}

do_compile() {
    cd ${S}

    install -d ${BUN_HOME}
    export HOME=${BUN_HOME}

    bun install --frozen-lockfile --no-progress
    for app in ${BUILTIN_WEBAPPS} ${EXAMPLE_WEBAPPS}; do
        bun run build --filter=@bridgething/webapp-${app}
    done
}

do_install() {
    install -d ${D}${BUILTIN_DIR}
    install -d ${D}${EXAMPLES_DIR}

    if [ "${FROM_SOURCE}" = "1" ]; then
        for app in ${BUILTIN_WEBAPPS}; do
            dist="${S}/packages/webapps/builtin/${app}/dist"
            if [ ! -f "${dist}/manifest.json" ]; then
                bbfatal "builtin ${app}: ${dist}/manifest.json missing after build"
            fi
            install -d ${D}${BUILTIN_DIR}/${app}
            cp -r "${dist}/." ${D}${BUILTIN_DIR}/${app}/
        done

        for app in ${EXAMPLE_WEBAPPS}; do
            dist="${S}/packages/webapps/catalog/${app}/dist"
            if [ ! -f "${dist}/manifest.json" ]; then
                bbfatal "example ${app}: ${dist}/manifest.json missing after build"
            fi
            ( cd "${dist}" && zip -r -X -q "${D}${EXAMPLES_DIR}/${app}.zip" . )
        done
    else
        for pair in ${BRIDGETHING_BUILTIN_ZIPS}; do
            app="${pair%%:*}"
            install -d ${D}${BUILTIN_DIR}/${app}
            unzip -q "${UNPACKDIR}/${pair#*:}" -d ${D}${BUILTIN_DIR}/${app}
            if [ ! -f "${D}${BUILTIN_DIR}/${app}/manifest.json" ]; then
                bbfatal "builtin ${app}: no manifest.json in the published zip"
            fi
        done

        for pair in ${BRIDGETHING_EXAMPLE_ZIPS}; do
            install -m 0644 "${UNPACKDIR}/${pair#*:}" ${D}${EXAMPLES_DIR}/${pair%%:*}.zip
        done
    fi

    chown -R root:root ${D}${BUILTIN_DIR} ${D}${EXAMPLES_DIR}
    find ${D}${BUILTIN_DIR} ${D}${EXAMPLES_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${BUILTIN_DIR} ${D}${EXAMPLES_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${BUILTIN_DIR} ${EXAMPLES_DIR}"
