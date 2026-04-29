do_install:append() {
    # Superbird has no wifi hardware. Strip any brcmfmac wifi firmware
    # the upstream linux-firmware recipe ships but doesn't assign to a
    # sub-package - wrynose's recipe leaves recently-added blobs
    # unpackaged, which fails the installed-vs-shipped QA check.
    rm -rf ${D}${nonarch_base_libdir}/firmware/brcm/brcmfmac*
}
