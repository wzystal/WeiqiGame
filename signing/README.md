# Release 签名（本地）

- `release.jks`：Release 签名证书（**已 gitignore，务必备份**）
- `secrets.local.properties`：密码（**已 gitignore**）
- 根目录 `keystore.properties`：本地 Gradle 用（**已 gitignore**）

## 1. 生成本仓库 keystore（每个 Android 项目各一套）

```bash
bash scripts/generate-release-keystore.sh
```

## 2. 写入 GitHub Secrets

```bash
gh auth login -h github.com
export DINGTALK_WEBHOOK='钉钉机器人 Webhook 完整 URL'
export PGYER_API_KEY='蒲公英 API Key（https://www.pgyer.com/account/api）'

# 写入当前 git remote 对应仓库
bash scripts/setup-github-secrets.sh

# 或指定 owner/repo
bash scripts/setup-github-secrets.sh wzystal/weiqiGame
```

验证：

```bash
gh secret list --repo wzystal/weiqiGame
```

## 3. 蒲公英分发

Release 流水线在 push `main` 后会自动上传 APK 到蒲公英（需配置 `PGYER_API_KEY` Secret），钉钉通知优先推送国内安装页链接。

本地手动上传：

```bash
export PGYER_API_KEY='你的 API Key'
chmod +x scripts/pgyer_upload.sh
./scripts/pgyer_upload.sh -k "$PGYER_API_KEY" -d "本地测试" app/build/outputs/apk/release/app-release.apk
```

## 4. 本地打 Release 包

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

密码见 `signing/secrets.local.properties`（仅本机，勿提交）。
