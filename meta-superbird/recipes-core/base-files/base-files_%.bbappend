FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://fstab"
SRC_URI += "file://motd file://issue"

hostname:pn-base-files = "${SUPERBIRD_HOSTNAME}"
