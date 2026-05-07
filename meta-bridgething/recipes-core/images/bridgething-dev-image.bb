SUMMARY = "Bridgething development image - kitchen-sink iteration target"
DESCRIPTION = "Same partition geometry as prod (stock AML MPT, 516 MB \
system slots) since squashfs makes the lean shape enough for the \
kitchen-sink install. Adds: cog + chromium-chromedriver + dev tools \
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

# Dev-specific auth: passwordless root login via UART + SSH. Prod
# also runs unauthed (community trust posture; see base inc) but
# inherits the openssh defaults rather than the explicit overrides.
IMAGE_FEATURES += "allow-empty-password allow-root-login empty-root-password"

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
