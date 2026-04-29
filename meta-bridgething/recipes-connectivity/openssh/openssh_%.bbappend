# Point sshd's host keys at /var/lib/ssh (settings partition). /etc/ssh
# lives on the read-only system partition, so the default
# `HostKey /etc/ssh/ssh_host_*_key` paths can't be written and
# sshdgenkeys.service fails to generate keys on first boot.
#
# bridgething-init's /etc/default/ssh sets SYSCONFDIR for the
# sshd_check_keys script, but the script reads HostKey paths from
# `sshd -G -f sshd_config`, so the override has to land in
# sshd_config itself. We strip the upstream HostKey lines and
# append our own.

do_install:append() {
    sed -i '/^#\?HostKey/d' ${D}${sysconfdir}/ssh/sshd_config
    cat >> ${D}${sysconfdir}/ssh/sshd_config <<EOF

# Bridgething: rootfs is read-only; persist host keys on /var/lib/ssh
# (settings partition). Keys are generated on first boot by
# sshdgenkeys.service.
HostKey /var/lib/ssh/ssh_host_rsa_key
HostKey /var/lib/ssh/ssh_host_ecdsa_key
HostKey /var/lib/ssh/ssh_host_ed25519_key
EOF
}
