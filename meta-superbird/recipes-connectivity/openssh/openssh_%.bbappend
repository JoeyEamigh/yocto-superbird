# /etc/ssh is ro on squashfs; host keys live on the data mount under /var/lib/ssh
do_install:append() {
    sed -i '/^#\?HostKey/d' ${D}${sysconfdir}/ssh/sshd_config
    cat >> ${D}${sysconfdir}/ssh/sshd_config <<EOF

HostKey /var/lib/ssh/ssh_host_rsa_key
HostKey /var/lib/ssh/ssh_host_ecdsa_key
HostKey /var/lib/ssh/ssh_host_ed25519_key
EOF
}
