SUMMARY = "Bridgething development image - kitchen-sink iteration target"
DESCRIPTION = "Same partition geometry as prod (stock AML MPT, 516 MB \
system slots) since squashfs makes the lean shape enough for the \
kitchen-sink install. Adds: chromium-chromedriver + dev tools \
(vim, htop, tmux, sshfs, glmark2), weston desktop-shell with the \
panel visible, weston VNC backend wired, and debug-friendly auth \
(allow-empty-password). \
\
Same flash mechanic (restorePartition on AML MPT names), same OTA \
flow, same boot path as prod - the divergence is install-set only. \
The persistent /opt/bridgething overlay (settings-partition bind \
that survives bootslot swaps + OTA) ships in both prod and dev now \
via packagegroup-bridgething-core."
LICENSE = "MIT"

require bridgething-image-base.inc

# Dev-specific debug surface: tools-debug pulls
# packagegroup-core-tools-debug (gdb + gdbserver + libc-mtrace +
# strace, ~14 MB) for live debugging of daemon code on-device.
# post-install-logging spills package-install messages during
# do_rootfs into the build log, which is useful when iterating on the
# image but pure noise on a prod build.
#
# Auth-related features (empty root password, SSH, serial autologin)
# are set at distro scope via kas/base.yml's EXTRA_IMAGE_FEATURES so
# prod gets them too (community trust posture; both images ship
# intentionally unauthed).
IMAGE_FEATURES += "tools-debug post-install-logging"

# Dev install extras. The dev packagegroup already RDEPENDS
# bridgething-weston-init-desktop, so a single packagegroup pull
# covers everything dev-specific.
IMAGE_INSTALL:append = " \
    packagegroup-bridgething-dev \
"

# Mark the running image's OTA identity. The companion's poll loop
# only auto-pushes when its configured channel matches what the
# device announces in BridgeThingMeta; dev devices stay on the dev
# channel until the user opts back to stable (which requires a
# reflash because the cross-channel zcks won't line up).
BRIDGETHING_CHANNEL = "dev"
BRIDGETHING_IMAGE_VARIANT = "dev"
# `-dev` suffix keeps composeVersion (`<daemon>+image.<image>`) unique
# across channels at the same DISTRO_VERSION - otherwise dev and
# stable both compose to `0.1.0+image.0.1.0` and the discover
# manifest's releases-by-version map can't hold both. Also lets the
# companion's OtaArtifactURLs derive distinct `images/<channel>/
# <imageVersion>/` paths on R2 without two channels landing in the
# same prefix.
BRIDGETHING_IMAGE_VERSION = "${DISTRO_VERSION}-dev"
