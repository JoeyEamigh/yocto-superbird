FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

# Swap in our A/B + persistent-data fstab. base-files' do_install
# installs the fstab from its unpack directory; a higher-priority
# file:// SRC_URI from this bbappend takes precedence.
SRC_URI += "file://fstab"

# Override poky's empty motd + issue with bridgething branding.
# The same FILESEXTRAPATHS shadow that swaps fstab also makes our
# motd / issue files win over the ones in poky/meta/.
SRC_URI += "file://motd file://issue"

# Set the device hostname. base-files reads `hostname` and writes
# /etc/hostname + /etc/hosts at do_install. Default would be the
# MACHINE name (`superbird`); override to the project identity.
hostname:pn-base-files = "bridgething"
