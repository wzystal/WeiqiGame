#!/usr/bin/env bash
# 将本地 Release 签名与钉钉 Webhook 写入指定 GitHub 仓库的 Secrets
# 需先: gh auth login -h github.com
#
# 用法:
#   bash scripts/setup-github-secrets.sh                    # 从当前仓库 git remote 推断 owner/repo
#   bash scripts/setup-github-secrets.sh wzystal/weiqiGame  # 指定仓库
#   bash scripts/setup-github-secrets.sh wzystal weiqiGame  # 用户名 + 仓库名
#
# 钉钉 / 蒲公英（可选）:
#   export DINGTALK_WEBHOOK='https://oapi.dingtalk.com/robot/send?access_token=...'
#   export PGYER_API_KEY='蒲公英 API Key'
#   bash scripts/setup-github-secrets.sh owner/repo
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SECRETS_FILE="$ROOT/signing/secrets.local.properties"
KEYSTORE="$ROOT/signing/release.jks"

usage() {
  cat <<'EOF'
用法:
  setup-github-secrets.sh [owner/repo]
  setup-github-secrets.sh <owner> <repo>

示例:
  setup-github-secrets.sh                      # 自动读取 origin 远程仓库
  setup-github-secrets.sh wzystal/weiqiGame
  setup-github-secrets.sh wzystal weiqiGame

环境变量:
  DINGTALK_WEBHOOK   若已 export，会一并写入该仓库 Secret
  PGYER_API_KEY      若已 export，会一并写入该仓库 Secret

前置:
  gh auth login -h github.com
  bash scripts/generate-release-keystore.sh   # 本仓库尚未生成 keystore 时
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

detect_repo_from_git() {
  local url owner repo
  url="$(git -C "$ROOT" remote get-url origin 2>/dev/null || true)"
  if [[ -z "$url" ]]; then
    echo "无法从 git remote 推断仓库，请显式传入 owner/repo" >&2
    return 1
  fi
  if [[ "$url" =~ git@github.com:([^/]+)/(.+)\.git$ ]]; then
    owner="${BASH_REMATCH[1]}"
    repo="${BASH_REMATCH[2]}"
  elif [[ "$url" =~ https://github.com/([^/]+)/(.+)\.git$ ]]; then
    owner="${BASH_REMATCH[1]}"
    repo="${BASH_REMATCH[2]}"
  elif [[ "$url" =~ https://github.com/([^/]+)/([^/]+)/?$ ]]; then
    owner="${BASH_REMATCH[1]}"
    repo="${BASH_REMATCH[2]%.git}"
  else
    echo "无法解析 remote URL: $url" >&2
    return 1
  fi
  echo "${owner}/${repo}"
}

resolve_repo() {
  case $# in
    0)
      detect_repo_from_git
      ;;
    1)
      if [[ "$1" != */* ]]; then
        echo "单个参数须为 owner/repo 格式，例如: wzystal/weiqiGame" >&2
        usage >&2
        exit 1
      fi
      echo "$1"
      ;;
    2)
      echo "$1/$2"
      ;;
    *)
      usage >&2
      exit 1
      ;;
  esac
}

REPO="$(resolve_repo "$@")"

if ! gh auth status >/dev/null 2>&1; then
  echo "请先执行: gh auth login -h github.com" >&2
  exit 1
fi

if [[ ! -f "$SECRETS_FILE" ]] || [[ ! -f "$KEYSTORE" ]]; then
  echo "缺少 signing/release.jks 或 signing/secrets.local.properties" >&2
  echo "请先在本项目执行: bash scripts/generate-release-keystore.sh" >&2
  exit 1
fi

if ! gh repo view "$REPO" --json name >/dev/null 2>&1; then
  echo "仓库不存在或无权限: $REPO" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$SECRETS_FILE"

echo "目标仓库: $REPO"
echo "写入 Release 签名 Secrets..."

gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO" --body "$(base64 -i "$KEYSTORE" | tr -d '\n')"
gh secret set RELEASE_STORE_PASSWORD --repo "$REPO" --body "$STORE_PASSWORD"
gh secret set RELEASE_KEY_ALIAS --repo "$REPO" --body "$KEY_ALIAS"
gh secret set RELEASE_KEY_PASSWORD --repo "$REPO" --body "$KEY_PASSWORD"

echo "已写入: RELEASE_KEYSTORE_BASE64, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD"

if [[ -n "${PGYER_API_KEY:-}" ]]; then
  gh secret set PGYER_API_KEY --repo "$REPO" --body "$PGYER_API_KEY"
  echo "已写入: PGYER_API_KEY"
else
  echo ""
  echo "未设置 PGYER_API_KEY。蒲公英上传步骤将跳过。需要时执行："
  echo "  export PGYER_API_KEY='蒲公英后台 API 信息页中的 API Key'"
  echo "  bash scripts/setup-github-secrets.sh $REPO"
  echo "或："
  echo "  gh secret set PGYER_API_KEY --repo $REPO"
fi

if [[ -n "${DINGTALK_WEBHOOK:-}" ]]; then
  gh secret set DINGTALK_WEBHOOK --repo "$REPO" --body "$DINGTALK_WEBHOOK"
  echo "已写入: DINGTALK_WEBHOOK"
else
  echo ""
  echo "未设置环境变量 DINGTALK_WEBHOOK。需要钉钉通知时执行："
  echo "  export DINGTALK_WEBHOOK='你的钉钉机器人 Webhook'"
  echo "  bash scripts/setup-github-secrets.sh $REPO"
  echo "或："
  echo "  gh secret set DINGTALK_WEBHOOK --repo $REPO"
fi

echo ""
echo "完成。验证: gh secret list --repo $REPO"
