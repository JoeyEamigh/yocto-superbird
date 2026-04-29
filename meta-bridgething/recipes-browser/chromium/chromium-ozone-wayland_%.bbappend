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

DEPENDS:append = " bindgen-cli-native"

# Rollup arm64 native — npm tarball pulled by Yocto's HTTP fetcher.
# The tarball wraps everything under `package/`; we strip-components when
# extracting in do_install_rollup_arm64_native below.
SRC_URI:append = " https://registry.npmjs.org/@rollup/rollup-linux-arm64-gnu/-/rollup-linux-arm64-gnu-4.22.4.tgz;name=rollup-arm64-gnu;unpack=0;subdir=rollup-arm64-gnu"
SRC_URI[rollup-arm64-gnu.sha256sum] = "15dacc4a625c90f790c199ebb06e3327baee2f4a2163e1ee13643b0d8c29ac37"

SRC_URI:append = " https://registry.npmjs.org/@esbuild/linux-arm64/-/linux-arm64-0.25.1.tgz;name=esbuild-arm64;unpack=0;subdir=esbuild-arm64"
SRC_URI[esbuild-arm64.sha256sum] = "70771c9212585cfd1b190465f92dae98d1d3fc4a4fab5cacbef71457ee08e254"

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
