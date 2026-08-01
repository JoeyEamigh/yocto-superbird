SUMMARY = "Microphone array recorder with a kiosk debug readout"
DESCRIPTION = "Records the raw 4-channel array and the beamformed mono to a USB drive"
HOMEPAGE = "https://github.com/JoeyEamigh/bridgething"

LICENSE = "MIT & CC-BY-NC-SA-4.0"
LIC_FILES_CHKSUM = " \
    file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
    file://${COMMON_LICENSE_DIR}/CC-BY-NC-SA-4.0;md5=277261f4a7c6c099822f8f5cb0180da0 \
"

MIC_DEBUG_GIT_URI = "gitsm://github.com/JoeyEamigh/bridgething.git;protocol=https;branch=main;destsuffix=${BP}"

SRC_URI = " \
    ${MIC_DEBUG_GIT_URI} \
    file://mic-debug.service \
    file://chromium-kiosk-mic-debug.conf \
"
SRCREV = "${AUTOREV}"
PV = "0.1.0+git${SRCPV}"

inherit cargo systemd pkgconfig

DEPENDS = "alsa-lib"

do_compile[network] = "1"
CARGO_DISABLE_BITBAKE_VENDORING = "1"
CARGO_BUILD_FLAGS:remove = "--frozen"
CARGO_BUILD_FLAGS:append = " -p bridgething-mic-debug --locked"

export DO_NOT_FORMAT = "1"

SYSTEMD_SERVICE:${PN} = "mic-debug.service"
SYSTEMD_AUTO_ENABLE = "enable"

WAKEWORD_DIR = "${datadir}/mic-debug/wakeword"
MODEL_SOURCE = "${S}/crates/wakeword/models/hey_bridgething.btww"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/target/${CARGO_TARGET_SUBDIR}/mic-debug ${D}${bindir}/mic-debug

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/mic-debug.service \
        ${D}${systemd_system_unitdir}/mic-debug.service

    install -d ${D}${systemd_system_unitdir}/chromium-kiosk.service.d
    install -m 0644 ${UNPACKDIR}/chromium-kiosk-mic-debug.conf \
        ${D}${systemd_system_unitdir}/chromium-kiosk.service.d/mic-debug.conf

    if [ ! -f ${MODEL_SOURCE} ]; then
        bbfatal "no wake word model at ${MODEL_SOURCE}; fetch it before building this image"
    fi
    install -d ${D}${WAKEWORD_DIR}
    install -m 0644 ${MODEL_SOURCE} ${D}${WAKEWORD_DIR}/hey_bridgething.btww
}

PACKAGES =+ "${PN}-model"

LICENSE:${PN}-model = "CC-BY-NC-SA-4.0"
LICENSE:${PN} = "MIT"

FILES:${PN}-model = "${WAKEWORD_DIR}"
FILES:${PN} = " \
    ${bindir}/mic-debug \
    ${systemd_system_unitdir}/mic-debug.service \
    ${systemd_system_unitdir}/chromium-kiosk.service.d/mic-debug.conf \
"

SUMMARY:${PN}-model = "Wake word phrase model for the microphone recorder"
RDEPENDS:${PN} += "${PN}-model"

INSANE_SKIP:${PN} += "already-stripped"
