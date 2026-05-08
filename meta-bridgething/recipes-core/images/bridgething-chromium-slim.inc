# Slim chromium so even the prod image (516 MB system partition) fits.
# Lives at IMAGE_POSTPROCESS time rather than chromium-package time on
# purpose: editing the chromium recipe (or its bbappend) invalidates
# the recipe hash and triggers a multi-hour from-scratch chromium
# rebuild. Doing the cut here at rootfs assembly leaves chromium's
# package + sstate-cache untouched and only re-runs the few-seconds
# rootfs assembly task.
#
# Two cuts, both shared between dev + prod (kiosk on dev doesn't speak
# non-en either, and SwiftShader is software-Vulkan fallback nobody
# ever exercises because Panfrost is our GL driver):
#
#   - 47 of 50 .pak language bundles in /usr/lib/chromium/locales/.
#     Saves ~46 MB. Kiosk only renders English; bridgething webapp
#     authors are an English-speaking community.
#   - /usr/lib/chromium/libvk_swiftshader.so + vk_swiftshader_icd.json.
#     Saves ~19 MB. SwiftShader is chromium's CPU-rendered Vulkan
#     fallback for systems without a Vulkan driver. We use --use-gl=egl
#     and route everything through Panfrost; Vulkan is never asked for.
#
# Both cuts are idempotent (find -delete and rm -f tolerate missing
# inputs) so this command can re-run on a partial rootfs.
bridgething_chromium_slim () {
    if [ -d ${IMAGE_ROOTFS}${libdir}/chromium/locales ]; then
        find ${IMAGE_ROOTFS}${libdir}/chromium/locales \
            -maxdepth 1 -type f -name '*.pak' \
            ! -name 'en.pak' \
            ! -name 'en-US.pak' \
            ! -name 'en-GB.pak' \
            -delete
    fi

    rm -f ${IMAGE_ROOTFS}${libdir}/chromium/libvk_swiftshader.so
    rm -f ${IMAGE_ROOTFS}${libdir}/chromium/vk_swiftshader_icd.json
}

ROOTFS_POSTPROCESS_COMMAND += "bridgething_chromium_slim;"

# Strip --incognito from /usr/bin/chromium's hardcoded CHROME_EXTRA_ARGS.
# meta-chromium's wrapper unconditionally injects --incognito ahead of
# our launcher's args, and there is no chromium CLI flag to un-set
# incognito once set. Incognito disables every on-disk persistence
# subsystem - V8 Code Cache, HTTP cache, IndexedDB, cookies, Local
# Storage - so --user-data-dir=/var/cache/bridgething/chromium is moot
# until --incognito is gone: every reboot is a cold cache for V8 + every
# webapp asset, and first-paint-after-boot is always the slow case.
# After this strip the cache populates normally; subsequent boots warm-
# start.
#
# Same image-postprocess rationale as bridgething_chromium_slim above:
# editing the chromium recipe (or its bbappend) invalidates chromium's
# package hash and triggers a multi-hour rebuild. Doing the cut here at
# rootfs assembly leaves chromium's package + sstate untouched.
#
# The wrapper is at ${libdir}/chromium/chromium-wrapper; ${bindir}/chromium
# is a symlink pointing at the absolute on-device path /usr/lib/chromium/
# chromium-wrapper, which sed would dereference against the BUILD HOST's
# root (where it does not exist) rather than IMAGE_ROOTFS. Editing the
# real file directly sidesteps the symlink entirely.
#
# Idempotent (sed finds nothing to substitute on a second run) and
# tolerant of the wrapper's exact whitespace - the leftover spaces get
# tokenized away when the shell exec's chromium-bin.
bridgething_chromium_unincognito () {
    if [ -f ${IMAGE_ROOTFS}${libdir}/chromium/chromium-wrapper ]; then
        sed -i 's/--incognito//g' ${IMAGE_ROOTFS}${libdir}/chromium/chromium-wrapper
    fi
}

ROOTFS_POSTPROCESS_COMMAND += "bridgething_chromium_unincognito;"
