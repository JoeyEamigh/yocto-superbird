SUMMARY = "Bridgething dev image extras - browsers, dev tools, automation"
DESCRIPTION = "Stuff we want on the dev image but never want on prod: \
both browser stacks (cog + chromium) for benchmark comparison, weston \
VNC backend (PACKAGECONFIG-driven, see meta-bridgething/conf/distro/ \
bridgething.conf) so a host can drive the compositor over USB-gadget, \
ydotool for synthetic input, and a kitchen-sink CLI tool set so we \
don't have to scp common utilities every time we boot a fresh dev \
image. None of these belong in prod - they're for our iteration loop."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

PACKAGES = "${PN}"

RDEPENDS:${PN} = " \
    \
    bridgething-weston-init-desktop \
    bridgething-dev-persist \
    \
    cog \
    wpewebkit \
    chromium-ozone-wayland-chromedriver \
    \
    weston-examples \
    weston-vnc-backend \
    libdrm-tests \
    kmscube \
    glmark2 \
    bridgething-gltest \
    \
    ydotool \
    \
    bash \
    coreutils \
    diffutils \
    findutils \
    grep \
    sed \
    tar \
    util-linux \
    \
    vim-tiny \
    rsync \
    sshfs-fuse \
    tmux \
    jq \
    file \
    \
    htop \
    iotop \
    lsof \
    strace \
    tcpdump \
    \
    i2c-tools \
    libgpiod-tools \
    devmem2 \
    evtest \
    \
    bridgething-mfi-proxy \
    \
    fastfetch \
"
