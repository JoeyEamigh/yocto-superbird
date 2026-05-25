# Class of Device 0x7c0000; iOS gates iAP2 accept on a non-zero CoD
do_install:append() {
    sed -i 's/^#Class = 0x000100$/Class = 0x7c0000/' \
        ${D}${sysconfdir}/bluetooth/main.conf
    # fail loudly if the sed silently no-op'd after an upstream format change
    grep -q '^Class = 0x7c0000$' ${D}${sysconfdir}/bluetooth/main.conf
}
