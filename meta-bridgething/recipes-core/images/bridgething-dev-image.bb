SUMMARY = "Bridgething development image"
DESCRIPTION = "Kitchen-sink iteration image: same partition geometry as prod, weston desktop-shell with panel + VNC, dev tools, debug auth."
LICENSE = "MIT"

require bridgething-image-base.inc

IMAGE_FEATURES += "tools-debug post-install-logging"

IMAGE_INSTALL:append = " \
    packagegroup-bridgething-dev \
"

BRIDGETHING_CHANNEL = "dev"
BRIDGETHING_IMAGE_VARIANT = "dev"
# -dev suffix keeps the composite version unique vs stable at the same DISTRO_VERSION
BRIDGETHING_IMAGE_VERSION = "${DISTRO_VERSION}-dev"
