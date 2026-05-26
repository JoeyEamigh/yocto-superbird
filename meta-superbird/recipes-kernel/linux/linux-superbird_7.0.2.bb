SUMMARY = "Mainline Linux 7.0.2 for Superbird"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

LINUX_VERSION = "7.0.2"
LINUX_VERSION_EXTENSION = "-superbird"
PV = "${LINUX_VERSION}+git${SRCPV}"

# pinned to v7.0.2; flip to AUTOINC against linux-7.0.y to ride point releases
SRCREV = "a703eaaf8bd68c416d20f907435038f409ace6e4"
SRC_URI = "git://git.kernel.org/pub/scm/linux/kernel/git/stable/linux.git;protocol=https;branch=linux-7.0.y;name=linux \
           file://superbird.cfg \
           file://superbird-disable.cfg \
           file://meson-g12a-superbird.dts \
           file://0001-drm-panel-st7701-add-spotify-superbird-variant.patch \
           file://0002-Bluetooth-btbcm-add-BCM20703A2-UART-subver.patch \
           file://0003-drm-bridge-dw-mipi-dsi-add-skip_first_enable-hook.patch \
           file://0004-drm-meson-preserve-display-state-across-bootloader-handoff.patch \
           file://0005-drm-meson-realign-DW-MIPI-DSI-lane-byte-counter.patch \
           file://0006-drm-meson-DW-DSI-PHY-testcode-0x44-before-PHY_LOCK.patch \
           file://0007-drm-meson-disable-pipeline-dither-on-MIPI-DSI.patch \
           file://0008-backlight-pwm-add-pwm-bootloader-on.patch \
           file://0009-Input-rotary-encoder-fall-back-to-fwnode_irq_get.patch \
           file://0010-Input-tlsc6x-add-Telink-tlsc6x-touchscreen-driver.patch \
           file://0011-tty-meson-uart-port-downstream-driver.patch \
           file://0012-pinctrl-meson-add-INPUT_ENABLE-and-bare-DRIVE_STRENGTH.patch \
           file://0013-irqchip-meson-gpio-encode-trigger-type-into-hwirq.patch \
           file://0014-drm-panfrost-drop-noisy-Purging-bytes-shrinker-print.patch"

inherit kernel

# point S at the real unpack dir so do_symlink_kernsrc moves source into STAGING_KERNEL_DIR
S = "${UNPACKDIR}/${BP}"

KERNEL_VERSION_SANITY_SKIP = "1"

COMPATIBLE_MACHINE = "^superbird$"

do_configure() {
    # drop the board dts into the in-tree amlogic dt dir + register it (idempotent)
    install -m 0644 ${UNPACKDIR}/meson-g12a-superbird.dts \
        ${S}/arch/arm64/boot/dts/amlogic/meson-g12a-superbird.dts
    if ! grep -q 'meson-g12a-superbird.dtb' ${S}/arch/arm64/boot/dts/amlogic/Makefile; then
        echo 'dtb-$(CONFIG_ARCH_MESON) += meson-g12a-superbird.dtb' >> \
            ${S}/arch/arm64/boot/dts/amlogic/Makefile
    fi

    # in-tree arm64 defconfig + our additive fragment + our negation fragment.
    # disable fragment trims arm64-defconfig drift (other-vendor SoCs, server bits,
    # ChromeOS firmware) - kconfig drops every dependent symbol on olddefconfig.
    oe_runmake -C ${S} O=${B} defconfig
    fragments=""
    [ -s ${UNPACKDIR}/superbird.cfg ] && fragments="$fragments ${UNPACKDIR}/superbird.cfg"
    [ -s ${UNPACKDIR}/superbird-disable.cfg ] && fragments="$fragments ${UNPACKDIR}/superbird-disable.cfg"
    if [ -n "$fragments" ]; then
        ${S}/scripts/kconfig/merge_config.sh -m -O ${B} ${B}/.config $fragments
    fi
    oe_runmake -C ${S} O=${B} olddefconfig
}
