#!/usr/bin/env bash
#
# 把本地的 release 签名导出成 GitHub Actions Secrets 需要的格式。
#
#   bash .github/scripts/export-signing-secrets.sh [keystore 路径]
#
# 默认读取 fktm-android/tmkey-release.jks，口令/别名从 fktm-android/keystore.properties 取。
#
# 输出里包含私钥与口令 —— 只贴到仓库的
# Settings → Secrets and variables → Actions，别外传、别提交、别贴到任何聊天窗口。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JKS="${1:-$REPO_ROOT/fktm-android/tmkey-release.jks}"
PROPS="$REPO_ROOT/fktm-android/keystore.properties"

if [ ! -f "$JKS" ]; then
  echo "找不到 keystore：$JKS" >&2
  echo "用法：bash .github/scripts/export-signing-secrets.sh [keystore 路径]" >&2
  exit 1
fi

prop() {
  [ -f "$PROPS" ] || return 0
  sed -n "s/^$1=//p" "$PROPS" | head -n 1
}

ALIAS="$(prop keyAlias)"
STORE_PASS="$(prop storePassword)"
KEY_PASS="$(prop keyPassword)"

# 尽量用 GNU base64（Git Bash / Linux 都有）；不换行输出，避免粘贴时被折断
if base64 -w0 </dev/null >/dev/null 2>&1; then
  B64="$(base64 -w0 "$JKS")"
else
  B64="$(base64 <"$JKS" | tr -d '\n')"
fi

cat <<EOF
在 GitHub 仓库里打开 Settings → Secrets and variables → Actions → New repository secret，
依次添加下面 4 个（名字必须完全一致）：

  KEYSTORE_BASE64      ← 下面这一整行
  KEYSTORE_PASSWORD    ← $([ -n "$STORE_PASS" ] && echo "已从 keystore.properties 读到" || echo "未读到，请手填")
  KEY_ALIAS            ← ${ALIAS:-(未读到，通常为 tmkey)}
  KEY_PASSWORD         ← $([ -n "$KEY_PASS" ] && echo "已从 keystore.properties 读到" || echo "未读到，请手填")

keystore：$JKS
别名：${ALIAS:-(未知)}
有效期至：$(keytool -list -v -keystore "$JKS" -storepass "${STORE_PASS:-}" 2>/dev/null | sed -n 's/^Valid from: //p' | head -n 1 || echo "(读取失败，口令可能不对)")

----- KEYSTORE_BASE64 的值（含换行，整段复制）-----
$B64
----- 结束 -----
EOF

echo
echo "提示：keystore 一旦替换，已装老版本的用户将无法覆盖安装（签名不一致）。"
echo "      $JKS 与 $PROPS 都在 .gitignore 里，请另行备份到安全的地方。"
