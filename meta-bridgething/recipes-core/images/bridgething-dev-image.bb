SUMMARY = "Bridgething development image - kitchen-sink iteration target"
DESCRIPTION = "Same partition geometry as prod (stock AML MPT, 516 MB \
system slots) since squashfs makes the lean shape enough for the \
kitchen-sink install. Adds: cog + chromium-chromedriver + dev tools \
(vim, htop, tmux, sshfs, glmark2), weston desktop-shell with the \
panel visible, weston VNC backend wired, persistent /opt/bridgething \
overlay bind-mounted from settings (so scp'd daemon / webapp survives \
bootslot swaps + OTA), and debug-friendly auth (allow-empty-password). \
\
Same flash mechanic (restorePartition on AML MPT names), same OTA \
flow, same boot path as prod - the divergence is install-set only."
LICENSE = "MIT"

require bridgething-image-base.inc

# Dev-specific auth: passwordless root login via UART + SSH. Prod
# also runs unauthed (community trust posture; see base inc) but
# inherits the openssh defaults rather than the explicit overrides.
IMAGE_FEATURES += "allow-empty-password allow-root-login empty-root-password"

# Dev install extras. The dev packagegroup already RDEPENDS
# bridgething-weston-init-desktop + bridgething-dev-persist, so a
# single packagegroup pull covers everything dev-specific.
IMAGE_INSTALL:append = " \
    packagegroup-bridgething-dev \
"
