SUMMARY = "fastfetch - system info tool, branded for Spotify Car Thing"
DESCRIPTION = "Bridgething image ships a /etc/fastfetch/config.jsonc that \
brands the header as 'Spotify Car Thing (Superbird)'."
HOMEPAGE = "https://github.com/fastfetch-cli/fastfetch"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=2090e7d93df7ad5a3d41f6fb4226ac76"

SRC_URI = " \
    git://github.com/fastfetch-cli/fastfetch.git;protocol=https;branch=master;tag=${PV};name=fastfetch \
    file://superbird.jsonc \
    file://ascii-logo.txt \
"
SRCREV_fastfetch = "4a61cdb1c9e4044ee959751e00bac1266dc6ebf9"

inherit cmake pkgconfig

# Keep the build lean - fastfetch has dozens of optional probes that drag
# in heavy deps (ImageMagick, RPM, ddcutil, libelf, dconf, etc.). For a
# 512-MiB embedded image we want only the cheap stuff. Disabled probes
# print "(unavailable)" if their info is requested but don't break the
# binary.
EXTRA_OECMAKE = " \
    -DENABLE_VULKAN=OFF \
    -DENABLE_WAYLAND=ON \
    -DENABLE_XCB_RANDR=OFF \
    -DENABLE_XRANDR=OFF \
    -DENABLE_DRM=ON \
    -DENABLE_DRM_AMDGPU=OFF \
    -DENABLE_GIO=OFF \
    -DENABLE_DCONF=OFF \
    -DENABLE_DBUS=OFF \
    -DENABLE_SQLITE3=OFF \
    -DENABLE_RPM=OFF \
    -DENABLE_IMAGEMAGICK7=OFF \
    -DENABLE_IMAGEMAGICK6=OFF \
    -DENABLE_CHAFA=OFF \
    -DENABLE_EGL=ON \
    -DENABLE_GLX=OFF \
    -DENABLE_OPENCL=OFF \
    -DENABLE_FREETYPE=OFF \
    -DENABLE_PULSE=OFF \
    -DENABLE_DDCUTIL=OFF \
    -DENABLE_ELF=OFF \
    -DBUILD_FLASHFETCH=OFF \
    -DBUILD_TESTS=OFF \
"

DEPENDS = "zlib wayland-native wayland libdrm mesa"

do_install:append() {
    install -d ${D}${sysconfdir}/fastfetch
    install -m 0644 ${UNPACKDIR}/superbird.jsonc ${D}${sysconfdir}/fastfetch/config.jsonc
    install -m 0644 ${UNPACKDIR}/ascii-logo.txt ${D}${sysconfdir}/fastfetch/ascii-logo.txt
}

FILES:${PN} += " \
    ${sysconfdir}/fastfetch \
    ${datadir}/bash-completion \
    ${datadir}/zsh \
    ${datadir}/fish \
    ${datadir}/licenses \
"

RDEPENDS:${PN} = "bash"
