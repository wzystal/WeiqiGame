#!/usr/bin/env bash
# 一次性生成本地 Release 签名（仅在本机执行，产物已 gitignore）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p signing

if [[ -f signing/release.jks ]]; then
  echo "已存在 signing/release.jks，跳过生成。"
  exit 0
fi

STORE_PASS="${STORE_PASS:-$(openssl rand -base64 18)}"
KEY_PASS="${KEY_PASS:-$STORE_PASS}"

keytool -genkeypair -v \
  -keystore signing/release.jks \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storetype PKCS12 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=WeiqiGame, OU=Dev, O=WeiqiGame, L=Hangzhou, ST=ZJ, C=CN"

cat > signing/secrets.local.properties <<EOF
# 本地专用 — 请备份到密码管理器
STORE_PASSWORD=$STORE_PASS
KEY_ALIAS=release
KEY_PASSWORD=$KEY_PASS
EOF

cat > keystore.properties <<EOF
storeFile=signing/release.jks
storePassword=$STORE_PASS
keyAlias=release
keyPassword=$KEY_PASS
EOF

chmod 600 signing/release.jks signing/secrets.local.properties keystore.properties

echo "完成: signing/release.jks + keystore.properties"
echo "密码保存在 signing/secrets.local.properties（勿提交 Git）"
