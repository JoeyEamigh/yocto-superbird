SUMMARY = "Stock Amlogic bootloader + FIP blobs"
DESCRIPTION = "Vendored stock BL2 + FIP_A/B blobs deployed for the flashthing class to bundle."
LICENSE = "CLOSED"

SRC_URI = " \
    file://bootloader.dump \
    file://fip_a.dump \
    file://fip_b.dump \
"

S = "${UNPACKDIR}"

# load-bearing boot blobs; sha-pinned to catch accidental edits
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

# deploy-only; not a rootfs package
do_install[noexec] = "1"
do_compile[noexec] = "1"
do_configure[noexec] = "1"

PACKAGES = ""
