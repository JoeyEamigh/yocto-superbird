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
    bridgething-daemon-dev-config \
    \
    cog \
    wpewebkit \
    \
    weston-examples \
    weston-vnc-backend \
    libdrm-tests \
    kmscube \
    glmark2 \
    bridgething-gltest \
    wayland-utils \
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
    curl \
    socat \
    \
    vim-tiny \
    rsync \
    sshfs-fuse \
    tmux \
    jq \
    file \
    iproute2 \
    iproute2-ss \
    lsof \
    psmisc \
    bind-utils \
    gdb \
    gdbserver \
    inotify-tools \
    mmc-utils \
    smartmontools \
    bluez5-noinst-tools \
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
