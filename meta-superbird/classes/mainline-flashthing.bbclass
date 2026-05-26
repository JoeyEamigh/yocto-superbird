# emit a flashthing zip for mainline u-boot first-flash, after do_image_complete.
#
# zip contents:
#   superbird-boot.bin   signed FIP + BL2; written to boot0 + boot1
#   <image>.wic          GPT + env + boot_a + root_a + boot_b + root_b
#   bandaid.ext4         optional; written at the bandaid partition's LBA
#   meta.json            metadataVersion: 2; uses writeBootPartition + writeUserArea
#
# bandaid is optional: set MAINLINE_FLASHTHING_WITH_BANDAID = "1" to splice it in.
# env-only zip emitted alongside, writing just the env partition LBA range.

DEPENDS:append = " superbird-uboot zip-native util-linux-native"

do_flashthing_zip[depends] += " \
    superbird-uboot:do_deploy \
    zip-native:do_populate_sysroot \
    util-linux-native:do_populate_sysroot \
"

MAINLINE_FLASHTHING_WITH_BANDAID ??= "0"

python __anonymous() {
    if d.getVar('MAINLINE_FLASHTHING_WITH_BANDAID') == "1":
        d.appendVar('do_flashthing_zip[depends]', ' bridgething-bandaid:do_deploy')
}

do_flashthing_zip[vardepsexclude] = "DATETIME"

python do_flashthing_zip () {
    import json
    import os
    import shutil
    import subprocess

    workdir = d.getVar('WORKDIR')
    deploy = d.getVar('DEPLOY_DIR_IMAGE')
    image_name = d.getVar('IMAGE_NAME')
    image_link_name = d.getVar('IMAGE_LINK_NAME')
    image_basename = d.getVar('IMAGE_BASENAME')
    distro_version = d.getVar('DISTRO_VERSION')
    with_bandaid = d.getVar('MAINLINE_FLASHTHING_WITH_BANDAID') == "1"

    inputs = {
        'boot_bin': os.path.join(deploy, "superbird-boot.bin"),
        'wic':      os.path.join(deploy, f"{image_link_name}.wic"),
        'uenv':     os.path.join(deploy, "superbird-uenv.vfat"),
    }
    if with_bandaid:
        inputs['bandaid'] = os.path.join(deploy, "bandaid.ext4")
    for label, path in inputs.items():
        if not os.path.isfile(path):
            bb.fatal(f"flashthing input missing ({label}): {path}")

    bb.note("[1/4] reading GPT from wic")
    result = subprocess.run(
        ["sfdisk", "--json", inputs['wic']],
        check=True, capture_output=True, text=True,
    )
    gpt = json.loads(result.stdout)
    partitions = {p['name']: p for p in gpt['partitiontable']['partitions']}
    if 'env' not in partitions:
        bb.fatal(f"wic GPT missing 'env' partition; got: {list(partitions)}")
    env_lba = int(partitions['env']['start'])
    bandaid_lba = None
    if with_bandaid:
        if 'bandaid' not in partitions:
            bb.fatal(f"wic GPT missing 'bandaid' partition; got: {list(partitions)}")
        bandaid_lba = int(partitions['bandaid']['start'])

    stage = os.path.join(workdir, "flashthing-stage")
    if os.path.isdir(stage):
        shutil.rmtree(stage)
    os.makedirs(stage)

    bb.note("[2/4] staging artifacts")
    shutil.copy(inputs['boot_bin'], os.path.join(stage, "superbird-boot.bin"))
    shutil.copy(inputs['wic'],      os.path.join(stage, "superbird.wic"))
    if with_bandaid:
        shutil.copy(inputs['bandaid'], os.path.join(stage, "bandaid.ext4"))

    bb.note("[3/4] rendering meta.json")
    steps = [
        {"type": "bulkcmd", "value": "amlmmc key"},
        {"type": "writeBootPartition", "value": {"hwpart": 1, "data": {"filePath": "superbird-boot.bin"}}},
        {"type": "writeBootPartition", "value": {"hwpart": 2, "data": {"filePath": "superbird-boot.bin"}}},
        {"type": "writeUserArea",      "value": {"lba": 0,   "data": {"filePath": "superbird.wic"}}},
    ]
    if with_bandaid:
        steps.append({"type": "writeUserArea",
                      "value": {"lba": bandaid_lba, "data": {"filePath": "bandaid.ext4"}}})
    meta = {
        "metadataVersion": 2,
        "name": image_basename,
        "version": distro_version,
        "description": "Mainline u-boot first flash: signed FIP + wic + bandaid",
        "steps": steps,
    }
    with open(os.path.join(stage, "meta.json"), "w") as f:
        json.dump(meta, f, indent=2)

    bb.note("[4/4] assembling flashthing zip")
    out_zip = os.path.join(deploy, f"{image_name}-flashthing.zip")
    stable = os.path.join(deploy, f"{image_link_name}-flashthing.zip")
    if os.path.lexists(out_zip):
        os.unlink(out_zip)
    if os.path.lexists(stable):
        os.unlink(stable)
    zip_inputs = ["superbird-boot.bin", "superbird.wic", "meta.json"]
    if with_bandaid:
        zip_inputs.insert(-1, "bandaid.ext4")
    subprocess.run(["zip", "-q", "-X", out_zip, *zip_inputs], cwd=stage, check=True)
    os.symlink(os.path.basename(out_zip), stable)

    env_stage = os.path.join(stage, "env-only")
    if os.path.isdir(env_stage):
        shutil.rmtree(env_stage)
    os.makedirs(env_stage)
    shutil.copy(inputs['uenv'], os.path.join(env_stage, "superbird-uenv.vfat"))
    env_meta = {
        "metadataVersion": 2,
        "name": f"{image_basename}-env-only",
        "version": distro_version,
        "description": "Env-only re-flash. Writes the env partition LBA range.",
        "steps": [
            {"type": "writeUserArea", "value": {"lba": env_lba,
                "data": {"filePath": "superbird-uenv.vfat"}}}
        ],
    }
    with open(os.path.join(env_stage, "meta.json"), "w") as f:
        json.dump(env_meta, f, indent=2)
    env_zip = os.path.join(deploy, f"{image_name}-flashthing-env-only.zip")
    env_stable = os.path.join(deploy, f"{image_link_name}-flashthing-env-only.zip")
    if os.path.lexists(env_zip):
        os.unlink(env_zip)
    if os.path.lexists(env_stable):
        os.unlink(env_stable)
    subprocess.run(["zip", "-q", "-X", env_zip, "superbird-uenv.vfat", "meta.json"],
                   cwd=env_stage, check=True)
    os.symlink(os.path.basename(env_zip), env_stable)

    bb.note(f"flashthing zip:          {stable} -> {os.path.basename(out_zip)}")
    bb.note(f"flashthing env-only zip: {env_stable} -> {os.path.basename(env_zip)}")
}

addtask flashthing_zip after do_image_complete before do_build
