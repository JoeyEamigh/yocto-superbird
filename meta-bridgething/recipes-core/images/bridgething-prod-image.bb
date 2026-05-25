SUMMARY = "Bridgething production image"
DESCRIPTION = "ext4 ro rootfs with the chromium kiosk launcher (cast_shell), weston kiosk-shell, Panfrost driving the Mali-G31."
LICENSE = "MIT"

# pinned before require so bridgething-image-base.inc picks ext4
SUPERBIRD_ROOTFS_TYPE = "ext4"

require bridgething-image-base.inc

# 516 MiB partition bounds; default overhead factor would overflow
IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "32768"

IMAGE_INSTALL:append = " \
    bridgething-weston-init-kiosk \
"

# the cast-shell PACKAGECONFIG drops gtk+3/nss/nspr; this catches the one rrecommends straggler
BAD_RECOMMENDATIONS += "adwaita-icon-theme-symbolic"

BRIDGETHING_IMAGE_VARIANT = "prod"
