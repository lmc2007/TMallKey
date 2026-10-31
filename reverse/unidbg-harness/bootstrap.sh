#!/usr/bin/env bash
# unidbg harness 一键环境：clone → 打补丁 → 本地安装 → 编译
# 前置：JDK 21、mvn、代理 127.0.0.1:7897（GitHub 需代理，且必须走 openssl 后端）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="${ROOT}/.tmp"
HERE="${ROOT}/reverse/unidbg-harness"
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Java/jdk-21}"
PROXY="${HTTPS_PROXY:-http://127.0.0.1:7897}"

mkdir -p "${SRC}"
cd "${SRC}"

if [ ! -d unidbg ]; then
  git -c http.sslBackend=openssl -c http.proxy="${PROXY}" \
      clone --depth 1 https://github.com/zhkl0228/unidbg.git unidbg
fi

cd unidbg
git checkout -- unidbg-android/src/main/java/com/github/unidbg/linux/ARM64SyscallHandler.java || true
git apply "${HERE}/patches/unidbg-fstatat-null.patch"

# 本地安装 0.9.10-SNAPSHOT（跳过 gpg 签名）
mvn -s "${HERE}/settings.xml" -B -q install \
    -pl unidbg-api,unidbg-android,backend/unicorn2 -am \
    -DskipTests -Dgpg.skip=true

cd "${HERE}"
mvn -s settings.xml -B -q compile
echo "[bootstrap] done. run: mvn -s settings.xml -q exec:java"
