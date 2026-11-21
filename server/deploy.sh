#!/usr/bin/env bash
set -euo pipefail
if [[ "${EUID}" -ne 0 ]]; then echo "Run as root: sudo bash deploy.sh"; exit 1; fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y curl ca-certificates gnupg openssl ufw
install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://sing-box.app/gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/sagernet.gpg
chmod 0644 /etc/apt/keyrings/sagernet.gpg
echo "deb [signed-by=/etc/apt/keyrings/sagernet.gpg] https://deb.sagernet.org/ * *" > /etc/apt/sources.list.d/sagernet.list
apt-get update
apt-get install -y sing-box
UUID="$(sing-box generate uuid)"
KEYS="$(sing-box generate reality-keypair)"
PRIVATE_KEY="$(printf '%s\n' "$KEYS" | awk '/PrivateKey/ {print $2}')"
PUBLIC_KEY="$(printf '%s\n' "$KEYS" | awk '/PublicKey/ {print $2}')"
SHORT_ID="$(openssl rand -hex 8)"
SERVER_IP="$(curl -4fsS https://api.ipify.org)"
cat > /etc/sing-box/config.json <<EOF
{
  "log": {"level": "info", "timestamp": true},
  "inbounds": [{
    "type": "vless",
    "tag": "vless-reality-in",
    "listen": "::",
    "listen_port": 443,
    "users": [{"uuid": "$UUID", "flow": "xtls-rprx-vision"}],
    "tls": {
      "enabled": true,
      "server_name": "www.microsoft.com",
      "reality": {
        "enabled": true,
        "handshake": {"server": "www.microsoft.com", "server_port": 443},
        "private_key": "$PRIVATE_KEY",
        "short_id": ["$SHORT_ID"]
      }
    }
  }],
  "outbounds": [{"type": "direct", "tag": "direct"}]
}
EOF
chmod 600 /etc/sing-box/config.json
systemctl enable sing-box
systemctl restart sing-box
ufw allow 22/tcp || true
ufw allow 443/tcp
ufw --force enable
echo
echo "=== NOVAVPN VLESS REALITY CLIENT LINK ==="
echo "vless://$UUID@$SERVER_IP:443?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome&pbk=$PUBLIC_KEY&sid=$SHORT_ID&type=tcp#NovaVPN"
echo
echo "Server status:"
systemctl --no-pager --full status sing-box | head -n 12
