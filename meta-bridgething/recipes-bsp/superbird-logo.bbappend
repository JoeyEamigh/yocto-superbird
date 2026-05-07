# Brand-override the BSP-level boot splash. The BSP's superbird-logo
# recipe ships a neutral default; bridgething-application images
# replace it with the bridgething-branded BMP. Other consumers
# (DeskThing, etc.) follow the same pattern in their own application
# layer: drop a 480x800 16-bit RGB565 BMP under files/ and set
# SUPERBIRD_BOOT_LOGO_NAME accordingly.

FILESEXTRAPATHS:prepend := "${THISDIR}/superbird-logo/files:"

SUPERBIRD_BOOT_LOGO_NAME = "bridgething-bootup.bmp"
