SUMMARY = "Bridgething wake word phrase model"
DESCRIPTION = "Baseline hey_bridgething classifier"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"

LICENSE = "CC-BY-NC-SA-4.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/CC-BY-NC-SA-4.0;md5=277261f4a7c6c099822f8f5cb0180da0"

require bridgething-wakeword-model-pin.inc

PV = "${BRIDGETHING_WAKEWORD_MODEL_VERSION}"

BRIDGETHING_OTA_BASE ?= "https://ota.bridgething.com"

PINNED = "${@'1' if d.getVar('BRIDGETHING_WAKEWORD_MODEL_SHA256') else '0'}"

MODEL_URI = "${BRIDGETHING_OTA_BASE}/wakeword/stable/model/${PV}/hey_bridgething.btww;name=model;downloadfilename=hey_bridgething-${PV}.btww;unpack=0"

SRC_URI = "${@d.getVar('MODEL_URI') if d.getVar('PINNED') == '1' else ''}"
SRC_URI[model.sha256sum] = "${BRIDGETHING_WAKEWORD_MODEL_SHA256}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

WAKEWORD_DIR = "${datadir}/bridgething/wakeword"

do_install() {
    if [ "${PINNED}" = "1" ]; then
        install -d ${D}${WAKEWORD_DIR}
        install -m 0644 ${UNPACKDIR}/hey_bridgething-${PV}.btww \
            ${D}${WAKEWORD_DIR}/hey_bridgething.btww
    fi
}

FILES:${PN} = "${WAKEWORD_DIR}"

ALLOW_EMPTY:${PN} = "1"
