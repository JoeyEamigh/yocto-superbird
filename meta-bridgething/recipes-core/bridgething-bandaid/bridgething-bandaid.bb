SUMMARY = "bandaid ext4 for bridgething"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^superbird$"

inherit deploy nopackages

DEPENDS = "e2fsprogs-native binutils-native"

BANDAID_IMG_BYTES = "${@int(d.getVar('SUPERBIRD_BANDAID_PART_SIZE')) * 1024 * 1024}"

BANDAID_PACKAGES = "bridgething-daemon bridgething-hub-webapp bridgething-stock-webapp"

do_compile[depends] += " \
    bridgething-daemon:do_package_write_ipk \
    bridgething-hub-webapp:do_package_write_ipk \
    bridgething-stock-webapp:do_package_write_ipk \
"

do_compile() {
    STAGE=${B}/bandaid-stage
    rm -rf ${STAGE} && mkdir -p ${STAGE}/bridgething

    for pkg in ${BANDAID_PACKAGES}; do
        ipk=$(ls ${DEPLOY_DIR_IPK}/*/${pkg}_*.ipk 2>/dev/null | head -1)
        if [ -z "$ipk" ]; then
            bbfatal "bandaid: ${pkg}_*.ipk not found under ${DEPLOY_DIR_IPK}/"
        fi
        tmp=$(mktemp -d -p ${B})
        (cd $tmp && ar x $ipk && tar -xf data.tar.*)
        if [ -d $tmp/usr/lib/bridgething ]; then
            cp -a $tmp/usr/lib/bridgething/. ${STAGE}/bridgething/
        fi
        rm -rf $tmp
    done

    rm -f ${B}/bandaid.ext4
    truncate -s ${BANDAID_IMG_BYTES} ${B}/bandaid.ext4
    mkfs.ext4 -F -L bandaid -m 0 -O ^has_journal -d ${STAGE} ${B}/bandaid.ext4
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/bandaid.ext4 ${DEPLOYDIR}/bandaid.ext4
}
addtask deploy after do_compile before do_build
