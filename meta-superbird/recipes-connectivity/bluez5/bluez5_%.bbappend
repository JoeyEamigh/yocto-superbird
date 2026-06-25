FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# make GATT LE-only: drop the BR/EDR ATT PSM-31 listener + GATT-over-BR/EDR SDP
# records so a dual-mode iOS opens a real LE link for GATT instead of bridging
# ATT onto the classic iAP2 ACL (which never serves AMS/ANCS and wedges the bond)
SRC_URI:append = " file://0001-gatt-database-make-gatt-le-only-drop-br-edr-att.patch"

# Class of Device 0x7c0000; iOS gates iAP2 accept on a non-zero CoD.
# Experimental + KernelExperimental enable kernel LL privacy (LE address resolution) so the iPhone's
# rotating resolvable private addresses resolve to the bonded LE identity on reconnect; without it
# bluez treats each reconnect RPA as a fresh temporary device and GCs it, taking the LE LTK with it,
# so AMS/ANCS only survive the initial pair.
do_install:append() {
    sed -i 's/^#Class = 0x000100$/Class = 0x7c0000/' \
        ${D}${sysconfdir}/bluetooth/main.conf
    sed -i 's/^#Experimental = false$/Experimental = true/' \
        ${D}${sysconfdir}/bluetooth/main.conf
    sed -i 's/^#KernelExperimental = false$/KernelExperimental = true/' \
        ${D}${sysconfdir}/bluetooth/main.conf
    # fail loudly if any sed silently no-op'd after an upstream format change
    grep -q '^Class = 0x7c0000$' ${D}${sysconfdir}/bluetooth/main.conf
    grep -q '^Experimental = true$' ${D}${sysconfdir}/bluetooth/main.conf
    grep -q '^KernelExperimental = true$' ${D}${sysconfdir}/bluetooth/main.conf
}
