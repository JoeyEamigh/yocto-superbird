SUMMARY = "u-boot env + flashthing env-only meta template"
DESCRIPTION = "Deploys env.txt and the env-only flashthing meta template for the flashthing image class to bundle."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://env.txt \
    file://meta-env-only.json.in \
"

S = "${UNPACKDIR}"

inherit deploy

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${S}/env.txt               ${DEPLOYDIR}/env.txt
    install -m 0644 ${S}/meta-env-only.json.in ${DEPLOYDIR}/meta-env-only.json.in
}
addtask deploy after do_compile before do_build

do_install[noexec] = "1"
do_compile[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
