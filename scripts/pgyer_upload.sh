#!/usr/bin/env bash
# 基于蒲公英官方 API 2.0 快速上传流程
# 文档: https://www.pgyer.com/doc/view/api_upload
set -euo pipefail

API_BASE="https://www.pgyer.com/apiv2"
MAX_POLL=60
POLL_INTERVAL=3
CURL_CONNECT_TIMEOUT=30
CURL_MAX_TIME=300
CURL_UPLOAD_MAX_TIME=1800

log() { echo "[pgyer] $*" >&2; }

usage() {
  cat <<'EOF'
Usage: pgyer_upload.sh -k <api_key> [-d <update_desc>] [-c <channel>] <apk_file>

上传 Android APK 到蒲公英，成功后输出安装页 URL（最后一行）。

环境变量（可选）:
  PGYER_CHANNEL_SHORTCUT  指定渠道短链接（可被 -c 覆盖）
EOF
  exit 1
}

api_key=""
update_desc=""
channel="${PGYER_CHANNEL_SHORTCUT:-}"
apk_file=""

while getopts 'k:d:c:h' opt; do
  case "$opt" in
    k) api_key="$OPTARG" ;;
    d) update_desc="$OPTARG" ;;
    c) channel="$OPTARG" ;;
    h) usage ;;
    *) usage ;;
  esac
done
shift $((OPTIND - 1))
apk_file="${1:-}"

[[ -n "$api_key" && -f "$apk_file" ]] || usage

api_key="$(printf '%s' "$api_key" | tr -d '[:space:]')"
if [[ -z "$api_key" ]]; then
  log "API Key 为空，请检查 PGYER_API_KEY Secret 或 -k 参数"
  exit 1
fi

file_name="${apk_file##*/}"

# Step 1: getCOSToken
token_args=(
  --connect-timeout "$CURL_CONNECT_TIMEOUT"
  --max-time "$CURL_MAX_TIME"
  --form-string "_api_key=${api_key}"
  --form-string "buildType=android"
  --form-string "buildInstallType=1"
  --form-string "buildUpdateDescription=${update_desc}"
)
if [[ -n "$channel" ]]; then
  token_args+=(--form-string "buildChannelShortcut=${channel}")
fi

token_body=$(
  curl -fsS -X POST "${API_BASE}/app/getCOSToken" \
    "${token_args[@]}"
)

code=$(echo "$token_body" | jq -r '.code')
if [[ "$code" != "0" ]]; then
  log "getCOSToken 失败: $(echo "$token_body" | jq -r '.message // .')"
  exit 1
fi

endpoint=$(echo "$token_body" | jq -r '.data.endpoint')
build_key=$(echo "$token_body" | jq -r '.data.key')
signature=$(echo "$token_body" | jq -r '.data.params.signature')
cos_token=$(echo "$token_body" | jq -r '.data.params["x-cos-security-token"]')
cos_key=$(echo "$token_body" | jq -r '.data.params.key')

# Step 2: upload file
http_code=$(
  curl -sS -o /dev/null -w '%{http_code}' \
    --connect-timeout "$CURL_CONNECT_TIMEOUT" \
    --max-time "$CURL_UPLOAD_MAX_TIME" \
    --form-string "key=${cos_key}" \
    --form-string "signature=${signature}" \
    --form-string "x-cos-security-token=${cos_token}" \
    --form-string "x-cos-meta-file-name=${file_name}" \
    -F "file=@${apk_file}" \
    "${endpoint}"
)

if [[ "$http_code" != "204" ]]; then
  log "文件上传失败，HTTP ${http_code}"
  exit 1
fi
log "APK 已上传，等待蒲公英发布..."

# Step 3: poll buildInfo
for ((i = 1; i <= MAX_POLL; i++)); do
  info=$(
    curl -fsS \
      --connect-timeout "$CURL_CONNECT_TIMEOUT" \
      --max-time "$CURL_MAX_TIME" \
      "${API_BASE}/app/buildInfo?_api_key=${api_key}&buildKey=${build_key}"
  )
  code=$(echo "$info" | jq -r '.code')

  if [[ "$code" == "0" ]]; then
    shortcut=$(echo "$info" | jq -r '.data.buildShortcutUrl')
    qrcode=$(echo "$info" | jq -r '.data.buildQRCodeURL')
    version=$(echo "$info" | jq -r '.data.buildVersion')
    version_no=$(echo "$info" | jq -r '.data.buildVersionNo')
    install_url="https://www.pgyer.com/${shortcut}"

    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
      {
        echo "install_url=${install_url}"
        echo "qrcode_url=${qrcode}"
        echo "shortcut=${shortcut}"
        echo "version=${version}"
        echo "version_no=${version_no}"
      } >>"$GITHUB_OUTPUT"
    fi

    log "发布成功: ${install_url} (${version}/${version_no})"
    echo "$install_url"
    exit 0
  fi

  if [[ "$code" == "1216" ]]; then
    log "发布失败: $(echo "$info" | jq -r '.message // .')"
    exit 1
  fi

  sleep "$POLL_INTERVAL"
done

log "发布超时（${MAX_POLL} 次轮询）"
exit 1
