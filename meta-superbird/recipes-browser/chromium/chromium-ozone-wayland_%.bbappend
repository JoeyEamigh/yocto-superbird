# aarch64-host fixes + cast_shell bring-up patches.
#
# aarch64-host gaps: bundled x86_64 rust toolchain, rollup native, esbuild native.
#
# cast_shell patches (chromecast-tree only; no-op on chrome target):
#   0001: load URL from cmd-line; skip cast_core gRPC when its switches are absent.
#   0002: page DevTools on IPv4 loopback so /json/* serves through the ssh tunnel.
#   0003: gate UI-DevTools on --enable-ui-devtools to avoid port collision with 9223.
#   0004: FakeConnectivityChecker; the real one storms the journal with no upstream.
#   0005: SetFullWindowBounds from CreateWindow so the WebContents isn't stuck at 0x0.
#   0006: PlatformWindow::SetFullscreen instead of Maximize to unmap weston's panel.
#   0007: opaque (SK_ColorBLACK) compositor backstop so viz produces quads frame 1.
#   0008: drop --disable-gpu-early-init so viz gets a GPU output surface.
#   0009: force ShouldCreatePrimaryPlane true so the root render pass promotes and the
#         wayland buffer pipeline runs end to end.
#   0010: enable_touch_input on CastWebViewParams so blink sees hardware touch.

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

DEPENDS:append = " bindgen-cli-native"

# rollup arm64 native; tarball wraps under `package/` so strip-components on extract
SRC_URI:append = " https://registry.npmjs.org/@rollup/rollup-linux-arm64-gnu/-/rollup-linux-arm64-gnu-4.22.4.tgz;name=rollup-arm64-gnu;unpack=0;subdir=rollup-arm64-gnu"
SRC_URI[rollup-arm64-gnu.sha256sum] = "15dacc4a625c90f790c199ebb06e3327baee2f4a2163e1ee13643b0d8c29ac37"

SRC_URI:append = " https://registry.npmjs.org/@esbuild/linux-arm64/-/linux-arm64-0.25.1.tgz;name=esbuild-arm64;unpack=0;subdir=esbuild-arm64"
SRC_URI[esbuild-arm64.sha256sum] = "70771c9212585cfd1b190465f92dae98d1d3fc4a4fab5cacbef71457ee08e254"

SRC_URI:append = " \
    file://0001-cast_shell-load-URL-from-cmd-line-make-cast_core-opt.patch \
    file://0002-cast_shell-bind-IPv4-loopback-for-page-DevTools.patch \
    file://0003-cast_shell-gate-UI-DevTools-on-switch-only.patch \
    file://0004-cast_shell-FakeConnectivityChecker-on-bridgething.patch \
    file://0005-cast_shell-SetFullWindowBounds-in-CreateWindow.patch \
    file://0006-cast_shell-fullscreen-platform-window-on-init.patch \
    file://0007-cast_shell-opaque-compositor-background.patch \
    file://0008-cast_shell-no-disable-gpu-early-init.patch \
    file://0009-cast_shell-force-primary-plane-on-ozone.patch \
    file://0010-cast_shell-enable-touch-input-in-CastServiceSimple.patch \
"

# cast-shell PACKAGECONFIG: build cast_shell instead of chrome + chromedriver. trims the binary
# and drops the gtk+3/nss/nspr/icon-theme stack from the rootfs.
PACKAGECONFIG[cast-shell] = " \
    enable_cast_receiver=true \
    is_castos=true \
    use_v4l2_codec=false \
    enable_widevine=false \
    enable_pdf=false \
    enable_extensions=false \
    ,, \
    "

python () {
    if 'cast-shell' in (d.getVar('PACKAGECONFIG') or '').split():
        d.setVar('CAST_SHELL_BUILD', '1')
}

# return 0 in a prepend block exits the combined task function, so upstream ninja never runs
do_compile:prepend() {
    if [ "${CAST_SHELL_BUILD}" = "1" ]; then
        ninja -v ${PARALLEL_MAKE} cast_shell
        return 0
    fi
}

do_install:prepend() {
    if [ "${CAST_SHELL_BUILD}" = "1" ]; then
        install -d ${D}${libdir}/chromium
        install -d ${D}${libdir}/chromium/assets
        install -d ${D}${libdir}/chromium/chromecast_locales

        install -m 0755 cast_shell ${D}${libdir}/chromium/chromium-bin

        install -m 0644 *.bin ${D}${libdir}/chromium/
        install -m 0644 icudtl.dat ${D}${libdir}/chromium/icudtl.dat
        install -m 0644 assets/cast_shell.pak ${D}${libdir}/chromium/assets/

        install -m 0644 chromecast_locales/*.pak ${D}${libdir}/chromium/chromecast_locales/

        if [ -e libEGL.so ]; then
            install -m 0644 libEGL.so ${D}${libdir}/chromium/
            install -m 0644 libGLESv2.so ${D}${libdir}/chromium/
        fi

        for so in libcast_graphics_1.0.so libcast_media_1.0.so libgrpc++.so; do
            if [ -e "$so" ]; then
                install -m 0644 "$so" ${D}${libdir}/chromium/
            fi
        done

        if [ -e chrome_crashpad_handler ]; then
            install -m 0755 chrome_crashpad_handler ${D}${libdir}/chromium/
        fi

        return 0
    fi
}

# cast_shell doesn't link gtk/nss/nspr/icon-theme; drop the chrome-bundle RDEPENDS
RDEPENDS:${PN}:remove = "${@bb.utils.contains('PACKAGECONFIG', 'cast-shell', 'gtk+3 gdk-pixbuf hicolor-icon-theme desktop-file-utils at-spi2-core nss nspr adwaita-icon-theme-symbolic', '', d)}"

# no chromedriver in the cast variant; drop the empty subpackage to keep QA quiet
PACKAGES:remove = "${@bb.utils.contains('PACKAGECONFIG', 'cast-shell', '${PN}-chromedriver', '', d)}"

do_replace_bundled_rust_tools () {
    bundle_bin="${S}/third_party/rust-toolchain/bin"
    [ -d "$bundle_bin" ] || return 0

    replace_one() {
        local name="$1"
        local target="$2"
        if [ -f "$target" ]; then
            rm -f "$bundle_bin/$name"
            ln -s "$target" "$bundle_bin/$name"
            bbnote "chromium: replaced bundled $name with $target"
        else
            bbfatal "expected $target to be in the native sysroot but it isn't"
        fi
    }

    replace_one bindgen "${STAGING_BINDIR_NATIVE}/bindgen"
    replace_one cargo   "${STAGING_BINDIR_NATIVE}/cargo"

    # rustfmt isn't in OE's rust-native; the bindgen-output format pass is cosmetic, stub it out
    cat > "$bundle_bin/rustfmt" <<EOF
#!/bin/sh
exit 0
EOF
    chmod +x "$bundle_bin/rustfmt"
    bbnote "chromium: stubbed rustfmt as no-op"
}
# run after do_configure so the native binaries we symlink to are staged
addtask replace_bundled_rust_tools after do_configure before do_compile
do_replace_bundled_rust_tools[depends] += " \
    bindgen-cli-native:do_populate_sysroot \
    rust-native:do_populate_sysroot \
"

do_install_rollup_arm64_native () {
    rollup_dir="${S}/third_party/devtools-frontend/src/node_modules/@rollup/rollup-linux-arm64-gnu"
    [ -d "${S}/third_party/devtools-frontend/src/node_modules/@rollup" ] || return 0
    [ -e "$rollup_dir/rollup.linux-arm64-gnu.node" ] && return 0
    rm -rf "$rollup_dir"
    mkdir -p "$rollup_dir"
    tar -C "$rollup_dir" --strip-components=1 \
        -xf "${UNPACKDIR}/rollup-arm64-gnu/rollup-linux-arm64-gnu-4.22.4.tgz"
    bbnote "chromium: installed @rollup/rollup-linux-arm64-gnu into $rollup_dir"
}
addtask install_rollup_arm64_native after do_unpack before do_compile

do_install_esbuild_arm64_native () {
    esbuild_bin="${S}/third_party/devtools-frontend/src/third_party/esbuild/esbuild"
    [ -e "$esbuild_bin" ] || return 0
    # idempotent: skip if already aarch64
    case "$(file -b "$esbuild_bin" 2>/dev/null)" in
        *aarch64*) return 0 ;;
    esac
    extract_dir="${WORKDIR}/esbuild-arm64-extract"
    rm -rf "$extract_dir"
    mkdir -p "$extract_dir"
    tar -C "$extract_dir" --strip-components=1 \
        -xf "${UNPACKDIR}/esbuild-arm64/linux-arm64-0.25.1.tgz"
    install -m 0755 "$extract_dir/bin/esbuild" "$esbuild_bin"
    bbnote "chromium: replaced bundled x86_64 esbuild with aarch64 binary"
}
addtask install_esbuild_arm64_native after do_unpack before do_compile
