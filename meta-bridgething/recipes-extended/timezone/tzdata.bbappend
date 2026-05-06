# Suppress the upstream /etc/timezone file. Bridgething's timezone state
# lives on /var/lib/timezone (settings partition); /etc/timezone is shipped
# as a symlink there by the bridgething-timezone recipe.
INSTALL_TIMEZONE_FILE = "0"

# Drop the /etc/localtime symlink the upstream do_install hard-codes.
# bridgething-timezone provides its own that points into the writable
# settings partition. Leaving the upstream entry in place would cause a
# do_rootfs file collision between the two packages.
do_install:append() {
    rm -f ${D}${sysconfdir}/localtime
}

# Strip /etc/localtime + /etc/timezone from the tzdata-core package
# manifest so the packaging step doesn't claim them. bridgething-timezone
# packages both paths.
FILES:tzdata-core:remove = "${sysconfdir}/localtime ${sysconfdir}/timezone"
