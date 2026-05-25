SUMMARY = "Superbird FIP signing tool (fip-tool), build-host only"
DESCRIPTION = "Builds ThingLabsOSS/superbird-fip-tools' pure-Go fip-tool as a \
native build-host binary, plus the public spotify aml-user-key.sig and the \
in-repo stock.bootloader.bin. superbird-uboot's do_deploy uses `fip-tool sign` \
(native stage-1 assembly + native signing, no aml_encrypt_g12a, no \
amlogic-boot-fip clone) to pack u-boot into a signed FIP, and `fip-tool flash \
ours --dry-run` to pair it with the stock BL2. Never installed into the target."
HOMEPAGE = "https://github.com/ThingLabsOSS/superbird-fip-tools"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=bfcc9fa683b26332011592972f78cafd"

# Single upstream now: the toolkit. The old amlogic-boot-fip clone + vendored
# x86 aml_encrypt_g12a are gone — fip-tool sign assembles + signs the FIP in
# pure Go from embedded board assets. destsuffix=${BP} lands it at the default
# S (${UNPACKDIR}/${BP}), so we don't assign S.
SRC_URI = "git://github.com/ThingLabsOSS/superbird-fip-tools.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "a591304f7c5c8298059fd1200b8e4ca98c0620ac"

# Build out-of-source: the default B==S would collide with the fip-tool/ source
# subdir (go build -o ${B}/fip-tool vs the ${S}/fip-tool package dir).
B = "${WORKDIR}/build"

inherit native
DEPENDS = "go-native"

# Build CGO_ENABLED=0: the USB transport (libusb via gousb) is build-tagged out
# of fip-tool, and the signing path (sign / flash --dry-run) needs no USB. The
# result links nothing and pulls no Go modules, so the build is hermetic +
# offline. GOTOOLCHAIN=local pins to go-native (go.mod floor is 1.26.2).
do_configure[noexec] = "1"

do_compile() {
    export GOROOT="${STAGING_LIBDIR_NATIVE}/go"
    export GOCACHE="${B}/.gocache"
    export GOPATH="${B}/.gopath"
    export GOPROXY="off"
    export GOFLAGS="-mod=readonly"
    export GOTOOLCHAIN="local"
    export CGO_ENABLED="0"
    cd ${S}/fip-tool
    ${STAGING_BINDIR_NATIVE}/go build -trimpath -o ${B}/fip-tool .
}

FIPTOOLS_DIR = "${datadir}/superbird-fip-tools"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/fip-tool ${D}${bindir}/fip-tool

    install -d ${D}${FIPTOOLS_DIR}/keys
    install -m 0644 ${S}/keys/aml-user-key.sig ${D}${FIPTOOLS_DIR}/keys/aml-user-key.sig
    install -m 0644 ${S}/stock.bootloader.bin ${D}${FIPTOOLS_DIR}/stock.bootloader.bin
}
