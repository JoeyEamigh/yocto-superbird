SUMMARY = "Bridgething wakeword detector"
DESCRIPTION = "Always-on wake word sidecar"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"

LICENSE = "MIT & CC-BY-NC-SA-4.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require bridgething-wakeword-pin.inc

PV = "${BRIDGETHING_WAKEWORD_RUNTIME_VERSION}"

BRIDGETHING_OTA_BASE ?= "https://ota.bridgething.com"
WAKEWORD_MODEL_PV = "${BRIDGETHING_WAKEWORD_MODEL_VERSION}"

SRC_URI = " \
    ${BRIDGETHING_OTA_BASE}/wakeword/stable/runtime/${PV}/wakeword-runtime.tar.gz;name=runtime;subdir=runtime \
    ${BRIDGETHING_OTA_BASE}/wakeword/stable/model/${WAKEWORD_MODEL_PV}/hey_bridgething.onnx;name=model;downloadfilename=hey_bridgething-${WAKEWORD_MODEL_PV}.onnx;unpack=0 \
    file://bridgething-wakeword.service \
"

SRC_URI[runtime.sha256sum] = "${BRIDGETHING_WAKEWORD_RUNTIME_SHA256}"
SRC_URI[model.sha256sum] = "${BRIDGETHING_WAKEWORD_MODEL_SHA256}"

inherit systemd

do_configure[noexec] = "1"
do_compile[noexec] = "1"

WAKEWORD_DIR = "${datadir}/bridgething-wakeword"

do_install() {
    install -d ${D}${WAKEWORD_DIR}
    install -m 0755 ${UNPACKDIR}/runtime/bridgething-wakeword ${D}${WAKEWORD_DIR}/bridgething-wakeword
    install -m 0644 ${UNPACKDIR}/runtime/melspectrogram.onnx ${D}${WAKEWORD_DIR}/melspectrogram.onnx
    install -m 0644 ${UNPACKDIR}/runtime/embedding_model.onnx ${D}${WAKEWORD_DIR}/embedding_model.onnx
    install -m 0644 ${UNPACKDIR}/runtime/NOTICE.md ${D}${WAKEWORD_DIR}/NOTICE.md

    install -m 0644 ${UNPACKDIR}/hey_bridgething-${WAKEWORD_MODEL_PV}.onnx \
        ${D}${WAKEWORD_DIR}/hey_bridgething.onnx

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/bridgething-wakeword.service \
        ${D}${systemd_system_unitdir}/bridgething-wakeword.service
}

SYSTEMD_SERVICE:${PN} = "bridgething-wakeword.service"
SYSTEMD_AUTO_ENABLE = "enable"

FILES:${PN} = " \
    ${WAKEWORD_DIR} \
    ${systemd_system_unitdir}/bridgething-wakeword.service \
"

RDEPENDS:${PN} += "systemd"

INSANE_SKIP:${PN} += "already-stripped"
