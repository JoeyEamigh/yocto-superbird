# Phase 2 image class: emit a stock-Amlogic-partition-shaped flash zip.
#
# Image recipes that inherit this class get a do_flashthing_zip task
# after do_image_complete. The task:
#   - mkbootimg's the kernel + dtb into a v0 boot.img
#   - Stages every partition we flash under its stock-shaped name
#     (bootloader.dump, fip_a.dump, fip_b.dump, logo.dump,
#     boot_a.dump, boot_b.dump, system_a.ext2, system_b.ext2)
#   - Renders meta.json from SUPERBIRD_PART_TABLE +
#     SUPERBIRD_OTA_SYSTEM_*_OFFSET vars (per-variant geometry)
#   - Emits both a full-flash zip and an env-only iteration zip
#
# Per-image partition geometry is parameterized via:
#   SUPERBIRD_PART_TABLE
#       Comma-separated name:start:size triples for the MBR overlay.
#       Mirrors the stock AML MPT (516 MB system slots / 256 MB
#       settings / 2 GB data). dev + prod images share the same
#       geometry now that squashfs makes the lean shape enough for
#       the kitchen-sink dev install.
#   SUPERBIRD_OTA_BOOT_A_OFFSET / SUPERBIRD_OTA_BOOT_B_OFFSET
#       Where to write boot.img in OTA. AML MPT positions for
#       boot_a / boot_b (Linux can't see them as block devices, so
#       we write via raw byte offsets on /dev/mmcblk0).
#   SUPERBIRD_OTA_SYSTEM_A_OFFSET / SUPERBIRD_OTA_SYSTEM_B_OFFSET
#       Where to write the rootfs in OTA. Both images use the
#       stock AML MPT system_a / system_b offsets.
#   SUPERBIRD_FLASH_VIA_AML_PARTITIONS
#       "yes" → flash uses restorePartition system_a / system_b
#       (bound to AML MPT geometry; this is what every variant
#       uses now). "no" → writeLargeMemory at SUPERBIRD_OTA_SYSTEM_*
#       offsets (legacy code path for non-stock geometries; kept
#       for posterity but no current image uses it).
#   SUPERBIRD_ROOTFS_TYPE
#       Deploy-file extension for the rootfs blob (e.g. "ext4",
#       "squashfs-zst"). Image recipes pin this to match their
#       IMAGE_FSTYPES. flashthing pulls
#       `<image-link-name>.<rootfs_ext>` from DEPLOY_DIR_IMAGE.
#
# First-flash behavior: A and B slots get identical content. Phase 3
# OTAs flip slots and only touch the inactive partitions.

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

    # SUPERBIRD_ROOTFS_TYPE is the deploy-file extension for the rootfs
    # image - "ext4", "squashfs-zst", "squashfs", etc. Image recipes pin
    # this to match their IMAGE_FSTYPES choice. Default ext4 keeps
    # backwards compatibility with the original geometry; squashfs
    # variants set "squashfs-zst" (or similar) to point flashthing at the
    # right file in DEPLOY_DIR_IMAGE.
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

    # Deploy boot.img + system.img stable symlinks for swupdate's
    # bbclass to pick up (it expects file names matching its
    # sw-description "filename =" entries). system.img points at the
    # rootfs file under whatever extension SUPERBIRD_ROOTFS_TYPE
    # selected - keeps the OTA path filesystem-agnostic.
    boot_named = os.path.join(deploy, f"{image_name}.boot.img")
    boot_link  = os.path.join(deploy, f"{image_link_name}.boot.img")
    shutil.copy(boot_a_path, boot_named)
    if os.path.lexists(boot_link):
        os.unlink(boot_link)
    os.symlink(os.path.basename(boot_named), boot_link)
    # Last-image-wins generic aliases. The dev OTA recipe
    # (bridgething-update.bb) consumes these directly. The prod
    # OTA recipe (bridgething-update-prod.bb) does NOT - it stages
    # its own symlinks in a per-recipe subdir of DEPLOY_DIR_IMAGE
    # against image-link-name-prefixed aliases, sidestepping the
    # race when both image variants build in parallel and the
    # last-finished flashthing_zip wins these names.
    for stable, target in (
        ("boot.img",    os.path.basename(boot_link)),
        ("system.img",  f"{image_link_name}.{rootfs_ext}"),
    ):
        p = os.path.join(deploy, stable)
        if os.path.lexists(p):
            os.unlink(p)
        os.symlink(target, p)

    # Stock u-boot's bootm doesn't pick up the dtb from boot.img's
    # second-stage slot - it always loads the dtb at fdt_addr,
    # which by default has a stale stock multi-dtb left over from
    # u-boot's own preboot. We dodge this by ALSO writing the raw
    # dtb to dtbo_a so env can `amlmmc read dtbo_a $fdt_addr ...`
    # before bootm. dtbo_a is 4MB and our dtb is ~50KB; we just
    # copy verbatim with no padding (amlmmc read uses byte count,
    # so trailing partition bytes don't matter).
    shutil.copy(inputs['dtb'], os.path.join(stage, "dtbo_a.dump"))
    shutil.copy(inputs['dtb'], os.path.join(stage, "dtbo_b.dump"))

    bb.note("[2/5] staging stock-shaped partitions")
    shutil.copy(inputs['bootloader'], os.path.join(stage, "bootloader.dump"))
    shutil.copy(inputs['fip_a'],      os.path.join(stage, "fip_a.dump"))
    shutil.copy(inputs['fip_b'],      os.path.join(stage, "fip_b.dump"))
    shutil.copy(inputs['logo'],       os.path.join(stage, "logo.dump"))
    shutil.copy(inputs['env'],        os.path.join(stage, "env.txt"))

    # Stage the rootfs under both names. For the AML-partition path
    # (production), restorePartition system_a / system_b each consume
    # a copy. For the offset path (dev), only one copy is needed but
    # we keep both filenames for symmetry with the prod zip layout.
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
        # Production: AML MPT positions for system_a / system_b.
        # restorePartition walks the MPT to find the right offset
        # and size - must match the stock 516MB system_a / system_b.
        for name in ("system_a", "system_b"):
            steps.append({
                "type": "restorePartition",
                "value": {"name": name, "data": {"filePath": f"{name}.ext2"}},
            })
    else:
        # Dev: bigger system slots overlap the AML MPT region
        # (system_b lives where MPT had settings + data start).
        # writeLargeMemory at our chosen offsets - bypasses MPT.
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

    if use_aml:
        # Erase via AML MPT names so x-systemd.makefs triggers a
        # fresh mkfs on first boot. Tail erases are best-effort -
        # any of them can hang USB; env is already written so
        # device boots fine even if the tail dies.
        for name in ("misc", "settings", "data"):
            steps.append({"type": "bulkcmd", "value": f"amlmmc erase {name}"})
    else:
        # Dev geometry doesn't match AML MPT for settings/data, so
        # amlmmc-erase those names would clobber random eMMC bytes.
        # Instead, write a small empty blob over the start of each
        # MBR settings/data partition to invalidate any leftover
        # ext4 superblock - x-systemd.makefs sees an unrecognized
        # filesystem and runs mkfs.ext4 on first boot.
        empty_path = os.path.join(stage, "empty-1m.bin")
        with open(empty_path, "wb") as f:
            f.write(b"\x00" * (1024 * 1024))
        for name, env_var in (
            ("settings", "SUPERBIRD_INVALIDATE_SETTINGS_OFFSET"),
            ("data",     "SUPERBIRD_INVALIDATE_DATA_OFFSET"),
        ):
            off = d.getVar(env_var)
            if off:
                steps.append({
                    "type": "writeLargeMemory",
                    "value": {
                        "address": int(off, 0),
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
    ]
    if not use_aml:
        zip_inputs.append("empty-1m.bin")
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
