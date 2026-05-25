# image class: emit a stock-aml-partition-shaped flashthing zip after do_image_complete.
#
# parameters:
#   SUPERBIRD_PART_TABLE             - name:start:size triples for the linux mbr overlay
#   SUPERBIRD_OTA_BOOT_{A,B}_OFFSET  - where to write boot.img in OTA
#   SUPERBIRD_OTA_SYSTEM_{A,B}_OFFSET - rootfs offsets in OTA
#   SUPERBIRD_FLASH_VIA_AML_PARTITIONS - "yes" = restorePartition; "no" = writeLargeMemory at offsets
#   SUPERBIRD_ROOTFS_TYPE            - rootfs deploy extension (ext4 / squashfs-lz4 / ...)
#
# first-flash writes identical content to slots A and B. OTAs flip the inactive slot.

SUPERBIRD_PART_TABLE ?= "system_a:0x10600000:0x2040b000,system_b:0x3120b000:0x2040b000,settings:0x52e16000:0x10000000,data:0x63616000:0x859ea000"

SUPERBIRD_OTA_BOOT_A_OFFSET   ?= "0xd600000"
SUPERBIRD_OTA_BOOT_B_OFFSET   ?= "0xee00000"
SUPERBIRD_OTA_SYSTEM_A_OFFSET ?= "0x10600000"
SUPERBIRD_OTA_SYSTEM_B_OFFSET ?= "0x3120b000"

SUPERBIRD_FLASH_VIA_AML_PARTITIONS ?= "yes"

DEPENDS:append = " \
    superbird-stock-firmware \
    superbird-logo \
    superbird-flash \
    mkbootimg-native \
    zip-native \
"

do_flashthing_zip[depends] += " \
    superbird-stock-firmware:do_deploy \
    superbird-logo:do_deploy \
    superbird-flash:do_deploy \
    virtual/kernel:do_deploy \
    mkbootimg-native:do_populate_sysroot \
    zip-native:do_populate_sysroot \
"

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
    layerdir = d.getVar('SUPERBIRD_LAYERDIR')
    kernel_dt = d.getVar('KERNEL_DEVICETREE') or ""

    part_table = d.getVar('SUPERBIRD_PART_TABLE')
    use_aml = (d.getVar('SUPERBIRD_FLASH_VIA_AML_PARTITIONS') or "yes").lower() == "yes"
    sys_a_off = d.getVar('SUPERBIRD_OTA_SYSTEM_A_OFFSET')
    sys_b_off = d.getVar('SUPERBIRD_OTA_SYSTEM_B_OFFSET')

    rootfs_ext = d.getVar('SUPERBIRD_ROOTFS_TYPE') or "ext4"

    stage = os.path.join(workdir, "flashthing-stage")
    if os.path.isdir(stage):
        shutil.rmtree(stage)
    os.makedirs(stage)

    dtb_basename = os.path.basename(kernel_dt.split()[0]) if kernel_dt else ""
    inputs = {
        'kernel':     os.path.join(deploy, "Image.gz"),
        'dtb':        os.path.join(deploy, dtb_basename) if dtb_basename else None,
        'rootfs':     os.path.join(deploy, f"{image_link_name}.{rootfs_ext}"),
        'bootloader': os.path.join(deploy, "bootloader.dump"),
        'fip_a':      os.path.join(deploy, "fip_a.dump"),
        'fip_b':      os.path.join(deploy, "fip_b.dump"),
        'logo':       os.path.join(deploy, "logo.img"),
        'env':        os.path.join(deploy, "env.txt"),
        'meta_env':   os.path.join(deploy, "meta-env-only.json.in"),
    }
    for label, path in inputs.items():
        if not path or not os.path.isfile(path):
            bb.fatal(f"flashthing input missing ({label}): {path}")

    bb.note("[1/5] building boot.img (kernel + dtb in second-stage)")
    boot_a_path = os.path.join(stage, "boot_a.dump")
    subprocess.run([
        "mkbootimg-superbird",
        "--kernel", inputs['kernel'],
        "--dtb",    inputs['dtb'],
        "--output", boot_a_path,
    ], check=True)
    shutil.copy(boot_a_path, os.path.join(stage, "boot_b.dump"))

    # stable boot.img / dtb / system.img symlinks for swupdate to pick up
    boot_named = os.path.join(deploy, f"{image_name}.boot.img")
    boot_link  = os.path.join(deploy, f"{image_link_name}.boot.img")
    shutil.copy(boot_a_path, boot_named)
    if os.path.lexists(boot_link):
        os.unlink(boot_link)
    os.symlink(os.path.basename(boot_named), boot_link)

    dtb_named = os.path.join(deploy, f"{image_name}.dtb")
    dtb_link  = os.path.join(deploy, f"{image_link_name}.dtb")
    shutil.copy(inputs['dtb'], dtb_named)
    if os.path.lexists(dtb_link):
        os.unlink(dtb_link)
    os.symlink(os.path.basename(dtb_named), dtb_link)

    # last-image-wins generic aliases; the delta OTA stages per-recipe to avoid this race
    for stable, target in (
        ("boot.img",    os.path.basename(boot_link)),
        ("dtb",         os.path.basename(dtb_link)),
        ("system.img",  f"{image_link_name}.{rootfs_ext}"),
    ):
        p = os.path.join(deploy, stable)
        if os.path.lexists(p):
            os.unlink(p)
        os.symlink(target, p)

    # stock u-boot bootm ignores boot.img's second-stage dtb; ship the raw dtb to dtbo_X
    shutil.copy(inputs['dtb'], os.path.join(stage, "dtbo_a.dump"))
    shutil.copy(inputs['dtb'], os.path.join(stage, "dtbo_b.dump"))

    bb.note("[2/5] staging stock-shaped partitions")
    shutil.copy(inputs['bootloader'], os.path.join(stage, "bootloader.dump"))
    shutil.copy(inputs['fip_a'],      os.path.join(stage, "fip_a.dump"))
    shutil.copy(inputs['fip_b'],      os.path.join(stage, "fip_b.dump"))
    shutil.copy(inputs['logo'],       os.path.join(stage, "logo.dump"))
    shutil.copy(inputs['env'],        os.path.join(stage, "env.txt"))

    # both slots get the same rootfs; restorePartition consumes them separately
    rootfs_a = os.path.join(stage, "system_a.ext2")
    rootfs_b = os.path.join(stage, "system_b.ext2")
    shutil.copy(inputs['rootfs'], rootfs_a)
    shutil.copy(inputs['rootfs'], rootfs_b)

    bb.note("[2b/5] overlaying linux-readable MBR onto bootloader.dump")
    subprocess.run([
        "python3",
        os.path.join(layerdir, "classes/files/write-mbr.py"),
        "--in-place", os.path.join(stage, "bootloader.dump"),
        "--table", part_table,
    ], check=True)

    bb.note(f"[3/5] rendering meta.json (variant: {'AML-partition' if use_aml else 'offset'})")
    steps: list = [
        {"type": "bulkcmd", "value": "amlmmc key"},
        {"type": "writeLargeMemory", "value": {
            "address": 0,
            "data": {"filePath": "bootloader.dump"},
            "blockLength": 4096,
        }},
    ]
    for name in ("fip_a", "fip_b", "logo", "dtbo_a", "dtbo_b", "boot_a", "boot_b"):
        steps.append({
            "type": "restorePartition",
            "value": {"name": name, "data": {"filePath": f"{name}.dump"}},
        })

    if use_aml:
        for name in ("system_a", "system_b"):
            steps.append({
                "type": "restorePartition",
                "value": {"name": name, "data": {"filePath": f"{name}.ext2"}},
            })
    else:
        # bigger system slots overlap the aml mpt region; writeLargeMemory at our offsets
        steps.append({
            "type": "writeLargeMemory",
            "value": {
                "address": int(sys_a_off, 0),
                "data": {"filePath": "system_a.ext2"},
                "blockLength": 4096,
            },
        })
        steps.append({
            "type": "writeLargeMemory",
            "value": {
                "address": int(sys_b_off, 0),
                "data": {"filePath": "system_b.ext2"},
                "blockLength": 4096,
            },
        })

    steps += [
        {"type": "bulkcmd", "value": "amlmmc env"},
        {"type": "writeEnv", "value": {"filePath": "env.txt"}},
        {"type": "bulkcmd", "value": "saveenv"},
    ]

    # zero the first 1MB of settings + data so x-systemd.makefs re-mkfs's them on first boot
    empty_path = os.path.join(stage, "empty-1m.bin")
    with open(empty_path, "wb") as f:
        f.write(b"\x00" * (1024 * 1024))

    if use_aml:
        # offsets come from the part_table; same values restorePartition uses above
        invalidate_offsets = {}
        for entry in part_table.split(","):
            name, off, _sz = entry.split(":")
            invalidate_offsets[name.strip()] = int(off.strip(), 0)
    else:
        # non-aml geometry diverges from the part_table; per-recipe env vars instead
        invalidate_offsets = {}
        for name, env_var in (
            ("settings", "SUPERBIRD_INVALIDATE_SETTINGS_OFFSET"),
            ("data",     "SUPERBIRD_INVALIDATE_DATA_OFFSET"),
        ):
            off = d.getVar(env_var)
            if off:
                invalidate_offsets[name] = int(off, 0)

    for name in ("settings", "data"):
        off = invalidate_offsets.get(name)
        if off is None:
            if use_aml:
                bb.fatal(f"SUPERBIRD_PART_TABLE missing '{name}' entry")
            continue
        steps.append({
            "type": "writeLargeMemory",
            "value": {
                "address": off,
                "data": {"filePath": "empty-1m.bin"},
                "blockLength": 4096,
            },
        })

    meta = {
        "$schema": "/dev/null",
        "metadataVersion": 1,
        "name": image_basename,
        "version": distro_version,
        "description": "Bridgething stock-layout flash. Writes named "
                       "Amlogic partitions via amlmmc + a Linux-readable "
                       "MBR overlay for mainline mounts.",
        "steps": steps,
    }
    with open(os.path.join(stage, "meta.json"), "w") as f:
        json.dump(meta, f, indent=2)

    bb.note("[4/5] assembling full-flash zip")
    out_zip = os.path.join(deploy, f"{image_name}-flashthing.zip")
    stable = os.path.join(deploy, f"{image_link_name}-flashthing.zip")
    if os.path.lexists(out_zip):
        os.unlink(out_zip)
    if os.path.lexists(stable):
        os.unlink(stable)
    zip_inputs = [
        "bootloader.dump",
        "fip_a.dump", "fip_b.dump",
        "logo.dump",
        "dtbo_a.dump", "dtbo_b.dump",
        "boot_a.dump", "boot_b.dump",
        "system_a.ext2", "system_b.ext2",
        "env.txt",
        "meta.json",
        "empty-1m.bin",
    ]
    subprocess.run(["zip", "-q", "-X", out_zip, *zip_inputs], cwd=stage, check=True)
    os.symlink(os.path.basename(out_zip), stable)

    bb.note("[5/5] assembling env-only zip")
    env_stage = os.path.join(stage, "env-only")
    if os.path.isdir(env_stage):
        shutil.rmtree(env_stage)
    os.makedirs(env_stage)
    shutil.copy(os.path.join(stage, "env.txt"), os.path.join(env_stage, "env.txt"))
    with open(inputs['meta_env'], "r") as f:
        meta_env_text = f.read()
    meta_env_text = (
        meta_env_text
        .replace("@IMAGE_BASENAME@", image_basename)
        .replace("@DISTRO_VERSION@", distro_version)
    )
    with open(os.path.join(env_stage, "meta.json"), "w") as f:
        f.write(meta_env_text)
    env_zip = os.path.join(deploy, f"{image_name}-flashthing-env-only.zip")
    env_stable = os.path.join(deploy, f"{image_link_name}-flashthing-env-only.zip")
    if os.path.lexists(env_zip):
        os.unlink(env_zip)
    if os.path.lexists(env_stable):
        os.unlink(env_stable)
    subprocess.run(["zip", "-q", "-X", env_zip, "env.txt", "meta.json"],
                   cwd=env_stage, check=True)
    os.symlink(os.path.basename(env_zip), env_stable)

    bb.note(f"flashthing zip:          {stable} -> {os.path.basename(out_zip)}")
    bb.note(f"flashthing env-only zip: {env_stable} -> {os.path.basename(env_zip)}")
}

addtask flashthing_zip after do_image_complete before do_build
