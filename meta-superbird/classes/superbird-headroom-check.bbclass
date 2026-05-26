# fail the build when wic geometry leaves no room for one ota write on the smallest target eMMC.

python __anonymous() {
    enforce = d.getVar('SUPERBIRD_REQUIRE_OTA_HEADROOM') or "0"
    if enforce.strip() != "1":
        return

    def mib(var):
        raw = d.getVar(var)
        if raw is None or raw.strip() == "":
            bb.fatal("superbird-headroom-check: %s is unset" % var)
        try:
            return int(raw.strip())
        except ValueError:
            bb.fatal("superbird-headroom-check: %s = %r is not an integer" % (var, raw))

    emmc   = mib('SUPERBIRD_MIN_EMMC_SIZE_MIB')
    env_p  = mib('SUPERBIRD_ENV_PART_SIZE')
    boot_p = mib('SUPERBIRD_BOOT_PART_SIZE')
    root_p = mib('SUPERBIRD_ROOT_PART_SIZE')
    band_p = mib('SUPERBIRD_BANDAID_PART_SIZE')
    margin = mib('SUPERBIRD_OTA_HEADROOM_MARGIN_SIZE')

    wic_fixed = env_p + 2 * boot_p + 2 * root_p + band_p
    data_floor = emmc - wic_fixed
    required = boot_p + root_p + margin

    if data_floor < required:
        bb.fatal(
            "superbird-headroom-check: data partition on the smallest target eMMC "
            "(%d MiB) would be %d MiB after the wic layout (%d MiB fixed), but an "
            "ota write needs %d MiB (boot %d + root %d + margin %d). raise "
            "SUPERBIRD_MIN_EMMC_SIZE_MIB if larger media is the new floor, or "
            "shrink SUPERBIRD_ROOT_PART_SIZE / SUPERBIRD_BANDAID_PART_SIZE."
            % (emmc, data_floor, wic_fixed, required, boot_p, root_p, margin)
        )
}
