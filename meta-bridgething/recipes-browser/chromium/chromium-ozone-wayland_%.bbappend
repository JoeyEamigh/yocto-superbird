# Aarch64-host fixes for chromium. The recipe is upstream-tested on x86_64
# hosts, where Google's pre-baked third_party prebuilts and node_modules
# binaries match the host arch. On aarch64 build hosts (our case — c8g
# Graviton), several bundled-x86_64 artefacts ENOEXEC during do_compile.
# brightsign / OSSystems / Igalia all run their CI on x86_64 hosts so
# nobody upstream has felt this — we patch each gap.
#
# Gap 1: third_party/rust-toolchain/bin/{bindgen,cargo,rustc,...} are
#        x86_64 ELF prebuilts. Override with OE's native equivalents.
# Gap 2: third_party/devtools-frontend/src/node_modules/@rollup ships
#        only @rollup/rollup-linux-x64-gnu. rollup 4.22.4's native.js
#        does require('@rollup/rollup-linux-arm64-gnu') on aarch64 and
#        has no ROLLUP_DISABLE_NATIVE escape hatch. Fetch the matching
#        npm tarball as an extra SRC_URI and extract it.
# Gap 3: third_party/devtools-frontend/src/third_party/esbuild/esbuild
#        is a 70 MB x86_64 ELF prebuilt of esbuild 0.25.1. esbuild
#        ships per-arch binaries via @esbuild/linux-{arch} npm
#        packages. Fetch @esbuild/linux-arm64-0.25.1 and overwrite
#        the bundled x86_64 binary with the arm64 one.

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

DEPENDS:append = " bindgen-cli-native"

# Rollup arm64 native — npm tarball pulled by Yocto's HTTP fetcher.
# The tarball wraps everything under `package/`; we strip-components when
# extracting in do_install_rollup_arm64_native below.
SRC_URI:append = " https://registry.npmjs.org/@rollup/rollup-linux-arm64-gnu/-/rollup-linux-arm64-gnu-4.22.4.tgz;name=rollup-arm64-gnu;unpack=0;subdir=rollup-arm64-gnu"
SRC_URI[rollup-arm64-gnu.sha256sum] = "15dacc4a625c90f790c199ebb06e3327baee2f4a2163e1ee13643b0d8c29ac37"

SRC_URI:append = " https://registry.npmjs.org/@esbuild/linux-arm64/-/linux-arm64-0.25.1.tgz;name=esbuild-arm64;unpack=0;subdir=esbuild-arm64"
SRC_URI[esbuild-arm64.sha256sum] = "70771c9212585cfd1b190465f92dae98d1d3fc4a4fab5cacbef71457ee08e254"

# cast_shell local patches. Minimal edits in chromecast/ that convert
# cast_shell into a usable single-page content embedder on this device:
#
#   0001: CoreCastService loads the URL from the cmd-line first and
#         only attempts the cast_core runtime gRPC bring-up when both
#         --cast-core-runtime-id and --runtime-service-path are
#         supplied. Without this, cast_shell ignores positional URL
#         args and terminates because Cast Platform Service is absent.
#   0002: page DevTools binds IPv4 loopback instead of IPv6 dual-stack
#         so the http handler thread actually serves /json/* through
#         the SSH tunnel from the host. The IPv6 path silently lands
#         in a "Cannot start http server" state on this kernel/glibc.
#   0003: UI-DevTools gated on --enable-ui-devtools only. cast_shell
#         built with is_castos=true defaults BUILD_ENG, and the
#         BUILD_ENG path unconditionally starts UI-DevTools on port
#         9223 which collides with the kiosk's page DevTools bind.
#   0004: ConnectivityChecker swapped for FakeConnectivityChecker (the
#         in-tree always-connected stub). The real checker hammers
#         connectivitycheck.gstatic.com/generate_204 every few seconds
#         and storms the journal with net_error=-105 - this device has
#         no upstream DNS / direct egress and nothing in the kiosk
#         path observes Connected() state anyway.
#   0005: CastContentWindowAura calls SetFullWindowBounds() from
#         CreateWindow() (right after window_ is bound) instead of
#         relying solely on DidStartNavigation. Upstream's GrantScreen
#         Access happens with window_ still null so its
#         SetFullWindowBounds short-circuits, and DidStartNavigation
#         only fires once after the WebContentsObserver is attached.
#         On single-process + immediate LoadUrl the navigation can
#         outrun the observer and the one resize signal is dropped,
#         leaving the WebContents child window at 0x0.
#   0006: CastWindowManagerAura calls PlatformWindow::SetFullscreen
#         (NOT Maximize - the latter leaves weston-desktop-shell's
#         panel visible and gives configure(800, 448); fullscreen
#         unmaps the panel for the toplevel and gives the full
#         configure(800, 480)).
#   0007: CastWindowManagerAura's compositor backstop is flipped from
#         SK_ColorTRANSPARENT to SK_ColorBLACK. Cast's transparency
#         assumes a hardware video plane below the compositor;
#         bridgething has none, and with alpha=0 the cc::LayerTree
#         HostImpl::AppendQuadsToFillScreen early-returns, viz emits
#         no quads in the first CompositorFrame, and the configure
#         ack handshake never completes.
#   0008: Removes {switches::kDisableGpuEarlyInit, ""} from
#         cast_browser_main_parts.cc kDefaultSwitches. The Cast
#         default exists for Nest Hub / TV hardware that boots in the
#         background and only starts the GPU when the user switches
#         the TV to cast input. On bridgething there is no such
#         event, and with the switch on, content/browser/browser_
#         main_loop.cc::CreateStartupTaskRunner sets establish_gpu_
#         channel = always_uses_gpu = false, leaving viz without a
#         GPU output surface.
#   0009: OverlayProcessorOzone::ShouldCreatePrimaryPlane unconditional
#         true. Upstream chromium 147 gates this on IS_CASTOS and
#         returns false, on the assumption that Cast hardware owns
#         its own primary plane through a hardware video plane the
#         media pipeline drives. On bridgething the cast_shell
#         renderer is the only producer of output; without this
#         override the root render pass is never promoted to a
#         primary plane OverlayCandidate, ScheduleOverlays in
#         SkiaOutputSurfaceImplOnGpu::PresentFrame is skipped, the
#         GbmPixmapWayland chain never runs, no CreateDmabufBased
#         Buffer mojo call reaches WaylandBufferManagerHost, and
#         CommitOverlays arrives at the host with an empty config
#         list. WaylandFrameManager records an empty frame, sends
#         exactly one ack_configure, never attaches a wl_buffer to
#         the wl_surface, and the kiosk wedges. This is the actual
#         cause of the GPU compositing wedge that --disable-gpu-
#         compositing had been working around since 2026-05-12.
#         Found via differential perfetto trace
#         (--trace-startup=wayland,viz,gpu): in the wedged build
#         WaylandBufferManagerHost::CommitOverlays fires but
#         WaylandBufferManagerHost::CreateDmabufBasedBuffer never
#         does, and GbmSurfacelessWayland::QueueWaylandOverlayConfig
#         never fires. Confirmed by reading the chromium 147 source
#         at components/viz/service/display/overlay_processor_ozone
#         .cc:501.
#   0010: CastServiceSimple::StartInternal sets enable_touch_input on
#         the CastWebViewParams it passes into CreateWebViewInternal.
#         Without this the mojom default (false) leaves CastContent
#         WindowAura's TouchBlocker pre-target handler activated, and
#         every ui::TouchEvent gets SetHandled() before reaching
#         blink. Pairs with the launcher's --enable-input flag which
#         opens the upstream CastWindowTreeHostAura::DispatchEvent
#         gate (early-returns on every event when enable_input_ is
#         false) and swaps the root window's NullWindowTargeter for
#         a real one. Either alone leaves hardware touch dead: the
#         WindowTreeHost gate drops events before they reach aura,
#         and even when they reach aura the TouchBlocker eats touch
#         specifically. Together they deliver hardware touch to
#         blink. CDP Input.dispatch* keeps working in all cases
#         because it injects downstream of aura.
#
# The screen-size seed itself (CastScreen primary display) is handled
# in the launcher via the existing chromecast switches
# --cast-graphics-width and --cast-graphics-height: CastDisplayConfig
# urator reads those at startup and seeds the primary display,
# replacing the 1280x720 fallback. No chromium patch needed for that
# path.
#
# All patches touch chromecast/ files only and are no-ops for the
# chrome target. They apply cleanly even when cast-shell PACKAGECONFIG
# is not selected.
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

# cast-shell PACKAGECONFIG. Switches the chromium recipe from building
# chrome + chromedriver to building cast_shell. The smaller binary
# (cast_shell strips to ~145 MB vs chrome's ~273 MB on aarch64 chromium
# 147) eliminates the squashfs page-fault storm under memory pressure
# that produced 9-second cold-of-kind animation latencies on this
# device.
#
# GN args:
#   enable_cast_receiver + is_castos turn the chromecast/ target tree
#     on.
#   use_v4l2_codec=false drops chromecast/'s cast_tests dep on
#     //media/gpu:video_decode_accelerator_tests, which only exists
#     when enable_av1_decoder + use_linux_video_acceleration — neither
#     of which the kiosk needs (single-page React app, no media
#     playback). Without this, gn gen fails resolving cast_tests.
#   enable_widevine=false drops the Widevine CDM (DRM for protected
#     video; useless on a device with no audio/video output).
#   enable_pdf=false drops the PDF viewer (no PDFs in a webapp
#     kiosk).
#   enable_extensions=false drops the chromium extensions subsystem
#     (no user-installable extensions in the kiosk).
PACKAGECONFIG[cast-shell] = " \
    enable_cast_receiver=true \
    is_castos=true \
    use_v4l2_codec=false \
    enable_widevine=false \
    enable_pdf=false \
    enable_extensions=false \
    ,, \
    "

# When cast-shell is selected the recipe builds and installs cast_shell
# + cast paks + cast graphics/media libs + ANGLE libs under
# /usr/lib/chromium/. cast_shell gets renamed to chromium-bin on
# install so the bridgething-chromium-launch path keeps working. chrome
# / chromedriver / the chromium-wrapper / chromium.desktop / icons are
# not produced in this variant.
python () {
    if 'cast-shell' in (d.getVar('PACKAGECONFIG') or '').split():
        d.setVar('CAST_SHELL_BUILD', '1')
}

# do_compile:prepend short-circuits the upstream `ninja chrome
# chrome_sandbox chromedriver.unstripped` when cast-shell is selected.
# `return 0` from a prepend block exits the combined task function so
# the upstream body never runs.
do_compile:prepend() {
    if [ "${CAST_SHELL_BUILD}" = "1" ]; then
        ninja -v ${PARALLEL_MAKE} cast_shell
        return 0
    fi
}

# do_install:prepend, same return-0 short-circuit. Installs the cast
# bundle layout: cast_shell as chromium-bin, V8 + ICU snapshot data,
# cast_shell resource pak under assets/, chromecast locale paks (image
# postprocess prunes everything except en* later), ANGLE libs, cast
# graphics/media libs, and the gRPC++ shared object cast_shell links
# against on this build.
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

# cast_shell variant doesn't carry gtk / nss / nspr / icon-theme / x11
# accessibility deps; drop the chrome-bundle RDEPENDS so the prod image
# stops pulling ~13-15 MB of gtk+3 stack + gdk-pixbuf + hicolor-icon-
# theme + adwaita-icon-theme + at-spi2-core + nss + nspr for a binary
# that never links against any of them.
RDEPENDS:${PN}:remove = "${@bb.utils.contains('PACKAGECONFIG', 'cast-shell', 'gtk+3 gdk-pixbuf hicolor-icon-theme desktop-file-utils at-spi2-core nss nspr adwaita-icon-theme-symbolic', '', d)}"

# Drop chromedriver sub-package when building cast_shell (the variant
# doesn't produce a chromedriver binary; without this Yocto emits a QA
# warning about an empty package).
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

    # rustfmt is not in OE's rust-native sysroot — it lives in the
    # target-only `rust-tools-rustfmt` package, which BBCLASSEXTEND
    # doesn't carry over. chromium's run_bindgen.py calls rustfmt
    # unconditionally on bindgen output, but that pass is purely
    # cosmetic — bindgen-generated bindings.rs is already valid.
    # Replace the bundled x86_64 rustfmt with a shell stub that
    # exits 0 without touching the file.
    cat > "$bundle_bin/rustfmt" <<EOF
#!/bin/sh
exit 0
EOF
    chmod +x "$bundle_bin/rustfmt"
    bbnote "chromium: stubbed rustfmt as no-op (cosmetic-only pass)"
}
# Run after do_configure so DEPENDS' do_populate_sysroot has staged the
# native binaries we symlink to; pin the deptask explicitly for safety.
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
    # Skip if the on-disk binary is already aarch64 (idempotent for
    # repeated do_compile retries within one build).
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
