#!/bin/bash
# 对已对齐的 APK 签名并校验；密钥信息全部来自环境变量。
set -euo pipefail
aligned=$1; out=$2
: "${MINDLINK_KEYSTORE:?keystore required}"
: "${MINDLINK_KS_PASS:?store password required}"
: "${MINDLINK_KEY_ALIAS:?alias required}"
APKSIGNER=${MINDLINK_APKSIGNER:-$(command -v apksigner)}
ZIPALIGN=${MINDLINK_ZIPALIGN:-$(command -v zipalign)}
"$APKSIGNER" sign --ks "$MINDLINK_KEYSTORE" --ks-pass "pass:$MINDLINK_KS_PASS" \
  --key-pass "pass:${MINDLINK_KEY_PASS:-$MINDLINK_KS_PASS}" \
  --ks-key-alias "$MINDLINK_KEY_ALIAS" --out "$out" "$aligned"
"$APKSIGNER" verify --print-certs "$out" | head -4
"$ZIPALIGN" -c 4 "$out" && echo ALIGN_OK
sha256sum "$out"
