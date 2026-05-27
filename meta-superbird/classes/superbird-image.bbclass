# shared image-side concerns for any superbird image. inherit from your
# image base alongside core-image. handles the per-image substitutions
# into /usr/share/superbird/meta.json.in that superbird-init reads at boot.
#
# downstream layers extending the meta.json shape add their own
# IMAGE_PREPROCESS_COMMAND that runs after superbird_meta_postprocess
# (awk-injecting new fields before the closing brace).

SUPERBIRD_IMAGE_BUILD_ID = "${DISTRO}-${DISTRO_VERSION}-${DATETIME}"
SUPERBIRD_IMAGE_BUILD_DATE = "${@d.getVar('DATETIME')[0:4]}-${@d.getVar('DATETIME')[4:6]}-${@d.getVar('DATETIME')[6:8]}T${@d.getVar('DATETIME')[8:10]}:${@d.getVar('DATETIME')[10:12]}:${@d.getVar('DATETIME')[12:14]}Z"

# vardepvalue overrides the value bitbake uses for hashing while leaving the
# runtime expansion alone: meta.json carries the live build timestamp,
# but task signatures stay stable across reparses where DATETIME ticks
# forward. var-level vardepsexclude alone doesn't propagate across the
# var->task boundary in this poky cut.
SUPERBIRD_IMAGE_BUILD_ID[vardepvalue] = "${DISTRO}-${DISTRO_VERSION}"
SUPERBIRD_IMAGE_BUILD_DATE[vardepvalue] = "stable"

superbird_meta_postprocess() {
    META=${IMAGE_ROOTFS}${datadir}/superbird/meta.json.in
    if [ ! -f "$META" ]; then
        return
    fi
    sed -i \
        -e "s|@IMAGE_BUILD_ID@|${SUPERBIRD_IMAGE_BUILD_ID}|g" \
        -e "s|@IMAGE_BUILD_DATE@|${SUPERBIRD_IMAGE_BUILD_DATE}|g" \
        "$META"
}

# leading + trailing whitespace matters: image.bbclass appends to
# do_image[vardeps] without a separator, so a vardeps entry without
# trailing space would concatenate into the next var name.
_SUPERBIRD_META_VARDEPS = " \
    SUPERBIRD_IMAGE_BUILD_ID \
    SUPERBIRD_IMAGE_BUILD_DATE \
"

# bitbake's sed-arg parser misses ${VAR} refs; declare deps so postprocess reruns.
superbird_meta_postprocess[vardeps] += "${_SUPERBIRD_META_VARDEPS}"
IMAGE_PREPROCESS_COMMAND[vardeps] += "${_SUPERBIRD_META_VARDEPS}"
do_image[vardeps] += "${_SUPERBIRD_META_VARDEPS}"

IMAGE_PREPROCESS_COMMAND:append = " superbird_meta_postprocess; "
