# /etc/ssh is ro; point HostKey at /var/lib/ssh on the settings partition
do_install:append() {
    sed -i '/^#\?HostKey/d' ${D}${sysconfdir}/ssh/sshd_config
    cat >> ${D}${sysconfdir}/ssh/sshd_config <<EOF

HostKey /var/lib/ssh/ssh_host_rsa_key
HostKey /var/lib/ssh/ssh_host_ecdsa_key
HostKey /var/lib/ssh/ssh_host_ed25519_key
EOF
}
