SUMMARY = "Bridgething development image"
DESCRIPTION = "Iteration image: weston desktop-shell with panel + VNC, dev tools, debug auth."
LICENSE = "MIT"

SUPERBIRD_ROOTFS_TYPE = "squashfs-lz4"

require bridgething-image-base.inc

IMAGE_FEATURES += "tools-debug post-install-logging"

IMAGE_INSTALL:append = " \
    packagegroup-bridgething-dev \
"

BRIDGETHING_CHANNEL = "dev"
BRIDGETHING_IMAGE_VARIANT = "dev"
# suffix keeps the composite version unique vs stable at the same DISTRO_VERSION.
BRIDGETHING_IMAGE_VERSION = "${DISTRO_VERSION}-dev"
