# Release 签名（本地）

通用脚本在 **`~/tools/scripts/`**，详见 pdf-studio 仓库 `signing/README.md` 或执行：

```bash
~/tools/scripts/generate-release-keystore.sh "$(pwd)"
~/tools/scripts/setup-github-secrets.sh --project-dir "$(pwd)"
~/tools/scripts/setup-shared-secrets.sh
```
