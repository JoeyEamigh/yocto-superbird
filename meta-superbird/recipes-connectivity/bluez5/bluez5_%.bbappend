# Set Class of Device to 0x7c0000 in the shipped main.conf. 0x7c is the
# upper service-class byte (Audio | Telephony | Information | Capturing
# | Object Transfer); Major and Minor are unspecified. iOS gates iAP2
# accessory acceptance on CoD; an adapter reporting 0x000000
# (Miscellaneous, the bluez default) lands in iOS Bluetooth Settings as
# "<name> is Not Supported" even when the iAP2 SDP record is published
# correctly. The stock Spotify Car Thing advertised the same upper
# service-class byte, so 0x7c0000 mirrors what every iPhone that ever
# bonded with a stock unit already saw.

do_install:append() {
    sed -i 's/^#Class = 0x000100$/Class = 0x7c0000/' \
        ${D}${sysconfdir}/bluetooth/main.conf
    # Fail the build loudly if upstream changed the default-class line
    # format and the sed above silently no-op'd. Better a build error
    # than shipping a Class=0 image and re-debugging "is not supported".
    grep -q '^Class = 0x7c0000$' ${D}${sysconfdir}/bluetooth/main.conf
}
