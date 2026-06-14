#!/usr/bin/env bash
# 智能安装：有业务代码变更则重新编译，否则直接安装已有 APK 到 USB 设备。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
STAMP_FILE=".build-and-install.stamp"
GRADLE="./gradlew"
ADB="${ADB:-adb}"
APP_PACKAGE="${APP_PACKAGE:-com.example.weiqigame}"
LAUNCHER_ACTIVITY="${LAUNCHER_ACTIVITY:-.MainActivity}"

log() { printf '[build-and-install] %s\n' "$*"; }
die() { log "ERROR: $*"; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

compute_source_hash() {
  find app \
    \( -path '*/build/*' -o -path '*/.gradle/*' \) -prune -o \
    -type f \( \
      -name '*.kt' -o -name '*.java' -o -name '*.xml' -o \
      -name '*.kts' -o -name '*.gradle' -o -name '*.properties' \
    \) -print \
    | LC_ALL=C sort \
    | while IFS= read -r file; do
        shasum -a 256 "$file"
      done \
    | shasum -a 256 \
    | awk '{print $1}'
}

pick_usb_device() {
  local devices
  devices="$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}')"
  if [[ -z "$devices" ]]; then
    die "未检测到已连接的 USB 设备。请确认已开启 USB 调试并执行: adb devices"
  fi
  local count
  count="$(printf '%s\n' "$devices" | wc -l | tr -d ' ')"
  if [[ "$count" -gt 1 ]]; then
    log "检测到多个 USB 设备，将安装到第一个: $(printf '%s\n' "$devices" | head -n1)"
  fi
  printf '%s\n' "$devices" | head -n1
}

install_apk() {
  local device="$1"
  [[ -f "$APK_PATH" ]] || die "APK 不存在: $APK_PATH"
  log "安装到设备 $device ..."
  "$ADB" -s "$device" install -r "$APK_PATH"
  "$ADB" -s "$device" shell am start -n "${APP_PACKAGE}/${LAUNCHER_ACTIVITY}" >/dev/null 2>&1 || true
  log "安装完成: $APK_PATH"
}

build_debug() {
  log "开始编译 debug APK ..."
  "$GRADLE" :app:assembleDebug --no-daemon
  [[ -f "$APK_PATH" ]] || die "编译完成但未找到 APK: $APK_PATH"
}

main() {
  require_cmd "$ADB"
  require_cmd shasum
  [[ -x "$GRADLE" ]] || die "未找到可执行的 gradlew"

  local current_hash saved_hash=""
  current_hash="$(compute_source_hash)"

  if [[ -f "$STAMP_FILE" ]]; then
    saved_hash="$(cat "$STAMP_FILE")"
  fi

  if [[ "$current_hash" == "$saved_hash" && -f "$APK_PATH" ]]; then
    log "业务代码无变更，跳过编译"
  else
    if [[ "$current_hash" != "$saved_hash" ]]; then
      log "检测到业务代码变更，重新编译"
    else
      log "未找到已编译 APK，开始编译"
    fi
    build_debug
    printf '%s' "$current_hash" > "$STAMP_FILE"
  fi

  install_apk "$(pick_usb_device)"
}

main "$@"
