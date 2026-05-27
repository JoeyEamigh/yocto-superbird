# builds bandaid.ext4 from a vendor's ipks. consumed at first flash to
# populate the bandaid partition; contents (daemon binary, webapps) can
# be swapped on the live device without a slot flip by writing into the
# opt-overlay bind-mount and restarting the service.
#
# inheritors set:
#   BANDAID_VENDOR    vendor name; matches the opt-overlay@<vendor>.service instance
#   BANDAID_PACKAGES  ipks to unpack; their /usr/lib/${BANDAID_VENDOR}/
#                     payload is rebased to /${BANDAID_VENDOR}/ inside the
#                     ext4 (opt-overlay@${BANDAID_VENDOR}.service binds the
#                     partition's /${BANDAID_VENDOR}/ to /opt/${BANDAID_VENDOR}).
#
# size from SUPERBIRD_BANDAID_PART_SIZE (MiB).

COMPATIBLE_MACHINE = "^superbird$"

inherit deploy nopackages

DEPENDS += "e2fsprogs-native binutils-native"

BANDAID_VENDOR ??= ""
BANDAID_PACKAGES ??= ""

python __anonymous() {
    for v in ("BANDAID_VENDOR", "BANDAID_PACKAGES"):
        if not d.getVar(v):
            bb.fatal("%s must be set by the inheriting recipe" % v)
}

BANDAID_IMG_BYTES = "${@int(d.getVar('SUPERBIRD_BANDAID_PART_SIZE')) * 1024 * 1024}"

do_compile[depends] += "${@' '.join('%s:do_package_write_ipk' % p for p in (d.getVar('BANDAID_PACKAGES') or '').split())}"

do_compile() {
    STAGE=${B}/bandaid-stage
    rm -rf ${STAGE} && mkdir -p ${STAGE}/${BANDAID_VENDOR}

    for pkg in ${BANDAID_PACKAGES}; do
        ipk=$(ls ${DEPLOY_DIR_IPK}/*/${pkg}_*.ipk 2>/dev/null | head -1)
        if [ -z "$ipk" ]; then
            bbfatal "bandaid: ${pkg}_*.ipk not found under ${DEPLOY_DIR_IPK}/"
        fi
        tmp=$(mktemp -d -p ${B})
        (cd $tmp && ar x $ipk && tar -xf data.tar.*)
        if [ -d $tmp/usr/lib/${BANDAID_VENDOR} ]; then
            cp -a $tmp/usr/lib/${BANDAID_VENDOR}/. ${STAGE}/${BANDAID_VENDOR}/
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
