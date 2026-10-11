#!/usr/bin/env python3
"""把刚构建好的 APK 登记进 `update/stable.json`，让客户端可以下载。

对标 yamibo-app 的 `.github/scripts/write-published-update-manifest.py`。
差别在于清单模型：yamibo 的源清单（`update/manifest.json`，isReady=false）与
客户端实际读的那份（`update-release` 分支上的 stable.json，isReady=true）是
两份文件，所以它写到 OUTPUT_DIR；TMKey 是「单分支单文件」—— `main` 上的
`stable.json` 既是源也是客户端读的那份，因此这里默认原地改写。

这一步必须在 Release 资产上传成功之后执行：先传包再置 isReady=true，
就不会出现「清单说可以下载、点进去 404」的窗口。

环境变量：

    MANIFEST_FILE  清单路径（默认 update/stable.json）
    OUTPUT_FILE    输出路径（默认与 MANIFEST_FILE 相同）
    RELEASE_URL    Release 页面地址，写进 releaseUrl
    ASSET_URL      APK 直链，写进 assets[0].url
    APK_NAME       资产文件名；给出时会校验 ASSET_URL 尾段与它一致
    APK_SHA256     小写十六进制 SHA-256
    APK_SIZE       字节数
"""

import os
import re
import sys
from pathlib import Path

from update_manifest_common import (
    dumps,
    first_bullet,
    load_manifest,
    manifest_path,
    read_changelog,
)

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def required_env(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        fail(f"缺少环境变量 {name}")
    return value


def main() -> None:
    path = manifest_path(os.environ.get("MANIFEST_FILE"))
    output = Path(os.environ.get("OUTPUT_FILE") or path)
    release_url = required_env("RELEASE_URL")
    asset_url = required_env("ASSET_URL")
    apk_name = (os.environ.get("APK_NAME") or "").strip()
    apk_sha256 = required_env("APK_SHA256").lower()
    apk_size_raw = required_env("APK_SIZE")

    if not path.is_file():
        fail(f"清单不存在：{path}")

    try:
        apk_size = int(apk_size_raw)
    except ValueError:
        fail(f"APK_SIZE 必须是整数，当前为 {apk_size_raw!r}")

    if not SHA256_PATTERN.match(apk_sha256):
        fail(f"APK_SHA256 必须是 64 位小写十六进制，当前为 {apk_sha256!r}")
    if apk_size <= 0:
        fail(f"APK_SIZE 必须为正，当前为 {apk_size}")
    if not release_url.startswith("https://"):
        fail(f"RELEASE_URL 必须是 https 直链，当前为 {release_url!r}")
    if not asset_url.startswith("https://"):
        fail(f"ASSET_URL 必须是 https 直链，当前为 {asset_url!r}")
    if apk_name and not asset_url.endswith("/" + apk_name):
        fail(f"ASSET_URL 的尾段与 APK_NAME 不一致：{asset_url} vs {apk_name}")

    manifest = load_manifest(path)
    version_code = int(manifest.get("versionCode") or 0)
    version_name = str(manifest.get("versionName", "")).strip()

    changelog_text, changelog_file = read_changelog(path, version_code)
    if changelog_text is None or not changelog_text.strip():
        fail(f"缺少或为空的 changelog：{changelog_file}")

    manifest["isReady"] = True
    manifest["releaseUrl"] = release_url
    manifest["assets"] = [
        {
            "type": "universal-apk",
            "platform": "android",
            "url": asset_url,
            "sha256": apk_sha256,
            "size": apk_size,
        }
    ]
    if not str(manifest.get("releaseNotes") or "").strip():
        manifest["releaseNotes"] = first_bullet(changelog_text) or f"TMKey {version_name}"

    text = dumps(manifest)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text, encoding="utf-8")

    print(f"已登记 {version_name} (versionCode {version_code}) → {output}")
    print(f"  releaseUrl: {release_url}")
    print(f"  asset:      {asset_url}")
    print(f"  sha256:     {apk_sha256}")
    print(f"  size:       {apk_size}")


if __name__ == "__main__":
    main()
