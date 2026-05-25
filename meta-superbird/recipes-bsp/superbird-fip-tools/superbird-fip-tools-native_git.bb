SUMMARY = "Superbird FIP signing tool (fip-tool), build-host only"
HOMEPAGE = "https://github.com/ThingLabsOSS/superbird-fip-tools"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=bfcc9fa683b26332011592972f78cafd"

SRC_URI = "git://github.com/ThingLabsOSS/superbird-fip-tools.git;protocol=https;branch=main;destsuffix=${BP}"
SRCREV = "b1992a16cc9aa393920b701d1d39c3fedfb68d8d"

# out-of-source: default B==S collides with the fip-tool/ source subdir.
B = "${WORKDIR}/build"

inherit native
DEPENDS = "go-native"

# CGO_ENABLED=0: USB transport is build-tagged out; sign/flash --dry-run needs no USB. hermetic + offline.
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
