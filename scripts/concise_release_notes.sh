#!/usr/bin/env bash
# 将 GitHub Release body 压缩为钉钉卡片用的简洁更新说明（Markdown 列表）。
set -euo pipefail

MAX_ITEMS="${MAX_ITEMS:-5}"
MAX_CHARS="${MAX_CHARS:-400}"

body="$(cat)"
if [[ -z "${body//[[:space:]]/}" ]]; then
  echo "（暂无更新说明）"
  exit 0
fi

bullets=$(
  awk '
    /^## What'"'"'s Changed/ { in_section=1; next }
    in_section && /^## / { exit }
    in_section && /^[*-] / { print }
  ' <<< "$body" | head -n "$MAX_ITEMS" | sed -E 's/ by @[^ ]+( in .*)?$//' || true
)

if [[ -z "${bullets//[[:space:]]/}" ]]; then
  bullets=$(grep -E '^[*-] ' <<< "$body" | head -n "$MAX_ITEMS" | sed -E 's/ by @[^ ]+( in .*)?$//' || true)
fi

if [[ -z "${bullets//[[:space:]]/}" ]]; then
  echo "（暂无更新说明）"
  exit 0
fi

result=""
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  line="${line#*- }"
  line="${line#* }"
  entry="- ${line}"
  if [[ -n "$result" ]]; then
    candidate="${result}"$'\n'"${entry}"
  else
    candidate="${entry}"
  fi
  if [[ ${#candidate} -gt $MAX_CHARS ]]; then
    break
  fi
  result="${candidate}"
done <<< "$bullets"

if [[ -z "$result" ]]; then
  echo "（暂无更新说明）"
else
  printf '%s\n' "$result"
fi
