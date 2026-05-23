SUMMARY = "Superbird FIP signing toolkit (BL33 -> signed boot.bin), build-host only"
DESCRIPTION = "Bundles ThingLabsOSS/superbird-fip-tools (fip-rebuild.sh, \
flash_boot_partition.py, the public spotify aml-user-key.sig, and the in-repo \
stock.bootloader.bin) together with LibreELEC's amlogic-boot-fip g12a \
components (incl. the prebuilt aml_encrypt_g12a). Installed into the native \
sysroot so superbird-uboot's do_deploy can pack + sign a flashable boot.bin \
with no out-of-tree tooling. Never installed into the target image."
HOMEPAGE = "https://github.com/ThingLabsOSS/superbird-fip-tools"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=bfcc9fa683b26332011592972f78cafd"

# Two upstreams: the toolkit (unpacked at ${S}), plus LibreELEC's
# amlogic-boot-fip layered into the amlogic-boot-fip/ subdir (where setup.sh
# would normally clone it). destsuffix=${BP} keeps the toolkit at the default
# S; oe-core sets S = ${UNPACKDIR}/${BP} itself, so we don't assign S.
SRC_URI = " \
    git://github.com/ThingLabsOSS/superbird-fip-tools.git;protocol=https;branch=main;name=fiptools;destsuffix=${BP} \
    git://github.com/LibreELEC/amlogic-boot-fip.git;protocol=https;branch=master;name=abf;destsuffix=${BP}/amlogic-boot-fip \
"
SRCREV_fiptools = "49bd1df250e54bcd5fbbb73c2d7b2eee538891ce"
SRCREV_abf = "42d372123631066fb77fbcbb612dc3eb41a3f6f9"
SRCREV_FORMAT = "fiptools_abf"

inherit native

# Prebuilt vendor blobs + scripts — nothing to configure or compile.
do_configure[noexec] = "1"
do_compile[noexec] = "1"

FIPTOOLS_DIR = "${datadir}/superbird-fip-tools"

do_install() {
    install -d ${D}${FIPTOOLS_DIR}
    cp -a ${S}/. ${D}${FIPTOOLS_DIR}/
    # Drop VCS metadata (both repos) and any stale build outputs.
    find ${D}${FIPTOOLS_DIR} -name .git -prune -exec rm -rf {} +
    rm -rf ${D}${FIPTOOLS_DIR}/.gitignore ${D}${FIPTOOLS_DIR}/out
    chmod +x ${D}${FIPTOOLS_DIR}/fip-rebuild.sh \
             ${D}${FIPTOOLS_DIR}/flash_boot_partition.py \
             ${D}${FIPTOOLS_DIR}/amlogic-boot-fip/build-fip.sh || true
    chmod +x ${D}${FIPTOOLS_DIR}/amlogic-boot-fip/*/aml_encrypt_g12a || true
}

# The tree ships a prebuilt static x86-64 aml_encrypt_g12a; it carries debug
# info and isn't stripped. It's a native build-host tool, never packaged into
# the target, so silence the QA bits that don't apply to a vendored blob.
INSANE_SKIP:${PN} += "already-stripped arch"
