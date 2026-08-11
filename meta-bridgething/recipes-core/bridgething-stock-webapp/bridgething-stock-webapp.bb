SUMMARY = "Stock Spotify webapp for bridgething"
HOMEPAGE = "https://github.com/thinglabsoss/superbird-webapp"
LICENSE = "CLOSED"

require bridgething-stock-webapp-pin.inc

PV = "${BRIDGETHING_STOCK_WEBAPP_VERSION}"

BRIDGETHING_STOCK_WEBAPP_PROVISION ?= "prebuilt"
BRIDGETHING_OTA_BASE ?= "https://ota.bridgething.com"

STOCK_ZIP = "bridgething-webapp-stock-${PV}.zip"
STOCK_URI = "${BRIDGETHING_OTA_BASE}/webapps/stable/stock/${PV}/stock.zip;name=stock;downloadfilename=${STOCK_ZIP};unpack=0"
STOCK_GIT_URI = "git://github.com/thinglabsoss/superbird-webapp.git;protocol=https;branch=bridgething"

FROM_SOURCE = "${@'1' if d.getVar('BRIDGETHING_STOCK_WEBAPP_PROVISION') == 'source' else '0'}"

SRC_URI = " \
    ${@d.getVar('STOCK_GIT_URI') if d.getVar('FROM_SOURCE') == '1' else d.getVar('STOCK_URI')} \
    file://spotify.svg \
    file://manifest.json \
"

SRC_URI[stock.sha256sum] = "${BRIDGETHING_STOCK_WEBAPP_SHA256}"

SRCREV = "${AUTOREV}"

DEPENDS = "${@'bun-native' if d.getVar('FROM_SOURCE') == '1' else 'unzip-native'}"

inherit allarch

WEBAPP_DIR = "${nonarch_libdir}/bridgething/webapps/stock"

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
    bun run build
}

do_install() {
    install -d ${D}${WEBAPP_DIR}

    if [ "${FROM_SOURCE}" = "1" ]; then
        cp -r ${S}/dist/. ${D}${WEBAPP_DIR}/
        install -m 0644 ${UNPACKDIR}/spotify.svg ${D}${WEBAPP_DIR}/spotify.svg
        install -m 0644 ${UNPACKDIR}/manifest.json ${D}${WEBAPP_DIR}/manifest.json
    else
        unzip -q "${UNPACKDIR}/${STOCK_ZIP}" -d ${D}${WEBAPP_DIR}
        if [ ! -f "${D}${WEBAPP_DIR}/manifest.json" ]; then
            bbfatal "stock webapp: no manifest.json in the published zip"
        fi
    fi

    chown -R root:root ${D}${WEBAPP_DIR}
    find ${D}${WEBAPP_DIR} -type d -exec chmod 0755 {} \;
    find ${D}${WEBAPP_DIR} -type f -exec chmod 0644 {} \;
}

FILES:${PN} = "${WEBAPP_DIR}"
