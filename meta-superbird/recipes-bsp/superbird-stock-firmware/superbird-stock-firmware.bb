SUMMARY = "Stock Amlogic bootloader + FIP blobs for the Superbird"
DESCRIPTION = "Vendored copies of the stock BL2 + FIP_A + FIP_B \
partition contents extracted from a Thing Labs 8.9.2 image. These \
are deployed to ${DEPLOY_DIR_IMAGE} for the flashthing image \
class to bundle into the final flash artifact. \
\
- bootloader.dump: 4 MB. v1.0-40 BL2 with embedded FIP. Drives the \
  ST7701S panel from u-boot (lights the backlight before kernel \
  hands off). \
- fip_a.dump / fip_b.dump: 4 MB each, identical bytes. v1.0-74 \
  AB-aware u-boot loaded by BL2 when the Amlogic MPT is in AB \
  state."
LICENSE = "CLOSED"

SRC_URI = " \
    file://bootloader.dump \
    file://fip_a.dump \
    file://fip_b.dump \
"

S = "${UNPACKDIR}"

# Catch bit-rot or accidental in-tree edits - these blobs are the
# load-bearing boot chain and are not regenerable from source.
SUPERBIRD_BOOTLOADER_SHA256 = "4def1db43ca4b508464d1496865d46f4702aed5e1b802daf6d320bc1c99b428e"
SUPERBIRD_FIP_A_SHA256 = "217096c4b3c3435756b8aadc28e80da7e14cc3aff28976fde5978e9964514250"
SUPERBIRD_FIP_B_SHA256 = "217096c4b3c3435756b8aadc28e80da7e14cc3aff28976fde5978e9964514250"

python do_verify_sha() {
    import hashlib
    s_dir = d.getVar('S')
    expected = {
        'bootloader.dump': d.getVar('SUPERBIRD_BOOTLOADER_SHA256'),
        'fip_a.dump': d.getVar('SUPERBIRD_FIP_A_SHA256'),
        'fip_b.dump': d.getVar('SUPERBIRD_FIP_B_SHA256'),
    }
    for name, want in expected.items():
        with open(f'{s_dir}/{name}', 'rb') as f:
            got = hashlib.sha256(f.read()).hexdigest()
        if got != want:
            bb.fatal(f'{name} sha256 mismatch: got {got}, want {want}')
}
addtask verify_sha after do_unpack before do_deploy

inherit deploy

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${S}/bootloader.dump ${DEPLOYDIR}/bootloader.dump
    install -m 0644 ${S}/fip_a.dump      ${DEPLOYDIR}/fip_a.dump
    install -m 0644 ${S}/fip_b.dump      ${DEPLOYDIR}/fip_b.dump
}
addtask deploy after do_compile before do_build

# No rootfs install - these are deploy-only build inputs.
do_install[noexec] = "1"
do_compile[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
