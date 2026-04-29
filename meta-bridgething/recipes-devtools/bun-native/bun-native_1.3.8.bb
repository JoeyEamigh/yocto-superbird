SUMMARY = "Bun JavaScript runtime/toolkit (build-host binary)"
DESCRIPTION = "Pre-built bun binary staged into the native sysroot for use \
during Yocto recipe builds. The binary is fetched directly from the upstream \
oven-sh/bun GitHub release and runs on the build host (BUILD_ARCH), not the \
target. Used by webapp recipes that need 'bun install' + 'bun run build'."
HOMEPAGE = "https://bun.com/"
LICENSE = "MIT & LGPL-2.0-only & BSD-2-Clause & Zlib"

inherit native

# bun publishes per-host-arch zips; map BUILD_ARCH to the upstream naming.
python () {
    arch_map = {'x86_64': 'x64', 'aarch64': 'aarch64'}
    build_arch = d.getVar('BUILD_ARCH')
    bun_arch = arch_map.get(build_arch)
    if not bun_arch:
        bb.fatal("bun-native: unsupported BUILD_ARCH %r (expected one of %s)"
                 % (build_arch, ', '.join(arch_map.keys())))
    d.setVar('BUN_ARCH', bun_arch)
}

SRC_URI = "https://github.com/oven-sh/bun/releases/download/bun-v${PV}/bun-linux-${BUN_ARCH}.zip;name=bun-${BUN_ARCH} \
           file://LICENSE.md"

SRC_URI[bun-x64.sha256sum] = "0322b17f0722da76a64298aad498225aedcbf6df1008a1dee45e16ecb226a3f1"
SRC_URI[bun-aarch64.sha256sum] = "4e9deb6814a7ec7f68725ddd97d0d7b4065bcda9a850f69d497567e995a7fa33"

LIC_FILES_CHKSUM = "file://${UNPACKDIR}/LICENSE.md;md5=3fc8b6c4e6874a69f48bc724eb8e4ce3"

S = "${UNPACKDIR}/bun-linux-${BUN_ARCH}"

do_configure() {
    :
}

do_compile() {
    :
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/bun ${D}${bindir}/bun
}

# the binary is a single statically linked executable; skip QA checks that
# don't apply (no ELF symbols to track for cross deps, no debug info).
INSANE_SKIP:${PN} += "already-stripped"
