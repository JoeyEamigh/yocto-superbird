do_install:append() {
    # superbird has no wifi; strip the brcmfmac blobs to clear unpackaged-files QA
    rm -rf ${D}${nonarch_base_libdir}/firmware/brcm/brcmfmac*
}
