#!/usr/bin/env python3
"""校验 `update/stable.json`。

用法：
    python3 .github/scripts/validate-update-manifest.py [清单路径] [--require-ready|--require-not-ready]

默认清单路径为 `update/stable.json`。校验内容：

- 必填字段与类型：`versionCode` 为正整数、`versionName` 形如 `x.y.z`、`isReady` 为布尔；
- `isReady=true` 时必须给出完整的 assets —— 每个资产要有 https 直链、
  64 位小写 sha256、非零 size，且 `type` 是 Android 认得的 `apk` / `universal-apk`；
- 同名 changelog（`update/changelogs/{versionCode}.changelog`）必须存在且非空。

它是 yamibo-app 里 `validateUpdateManifest` / `validatePublishedUpdateManifest`
两个 gradle 任务的对应物 —— TMKey 没有 buildSrc，一个独立脚本更省事，
本地也能直接跑，不用等 Gradle 启动。
"""

import argparse
import re
import sys

from update_manifest_common import (
    changelog_path,
    load_manifest,
    manifest_path,
    read_changelog,
)

VERSION_NAME_PATTERN = re.compile(r"^\d+\.\d+\.\d+$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
ASSET_TYPES = {"apk", "universal-apk"}


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def validate(manifest: dict, path, require_ready: bool, require_not_ready: bool) -> None:
    channel = manifest.get("channel")
    if not isinstance(channel, str) or not channel.strip():
        fail(f"{path}: channel 必须是非空字符串")

    version_name = manifest.get("versionName")
    if not isinstance(version_name, str) or not VERSION_NAME_PATTERN.match(version_name.strip()):
        fail(f"{path}: versionName 必须是 x.y.z 形式，当前为 {version_name!r}")

    version_code = manifest.get("versionCode")
    if not isinstance(version_code, int) or isinstance(version_code, bool) or version_code <= 0:
        fail(f"{path}: versionCode 必须是正整数，当前为 {version_code!r}")

    is_ready = manifest.get("isReady")
    if not isinstance(is_ready, bool):
        fail(f"{path}: isReady 必须是布尔值，当前为 {is_ready!r}")

    min_version_code = manifest.get("minVersionCode")
    if min_version_code is not None:
        if not isinstance(min_version_code, int) or isinstance(min_version_code, bool) or min_version_code <= 0:
            fail(f"{path}: minVersionCode 必须是正整数，当前为 {min_version_code!r}")
        if min_version_code > version_code:
            fail(
                f"{path}: minVersionCode({min_version_code}) 不能大于 versionCode({version_code})"
            )

    release_notes = manifest.get("releaseNotes")
    if release_notes is not None and not isinstance(release_notes, str):
        fail(f"{path}: releaseNotes 必须是字符串")

    release_url = manifest.get("releaseUrl")
    if release_url is not None and (
        not isinstance(release_url, str) or not release_url.strip().startswith("https://")
    ):
        fail(f"{path}: releaseUrl 必须是以 https:// 开头的字符串")

    assets = manifest.get("assets")
    if not isinstance(assets, list):
        fail(f"{path}: assets 必须是数组")

    if is_ready:
        if not isinstance(release_url, str) or not release_url.strip():
            fail(f"{path}: isReady=true 时必须填写 releaseUrl")
        if not assets:
            fail(f"{path}: isReady=true 时 assets 不能为空 —— 客户端会找不到可下载的 APK")
        for index, asset in enumerate(assets):
            where = f"{path}: assets[{index}]"
            if not isinstance(asset, dict):
                fail(f"{where} 必须是对象")
            url = asset.get("url")
            if not isinstance(url, str) or not url.strip().startswith("https://"):
                fail(f"{where}.url 必须是以 https:// 开头的直链")
            sha256 = asset.get("sha256")
            if not isinstance(sha256, str) or not SHA256_PATTERN.match(sha256):
                fail(f"{where}.sha256 必须是 64 位小写十六进制")
            size = asset.get("size")
            if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
                fail(f"{where}.size 必须是正整数")
            asset_type = asset.get("type")
            if not isinstance(asset_type, str) or asset_type.lower() not in ASSET_TYPES:
                fail(f"{where}.type 必须是 {sorted(ASSET_TYPES)} 之一，当前为 {asset_type!r}")
    else:
        if assets:
            print(
                f"::warning::{path}: isReady=false 但 assets 非空 —— 客户端不会展示这些资产，"
                "确认这是有意的（灰度中）"
            )

    text, changelog_file = read_changelog(path, version_code)
    if text is None:
        fail(f"{path}: 缺少 changelog：{changelog_file}（客户端会拿不到更新说明）")
    if not text.strip():
        fail(f"{path}: changelog 内容为空：{changelog_file}")

    if require_ready and not is_ready:
        fail(f"{path}: 该步骤要求 isReady=true，但当前为 false")
    if require_not_ready and is_ready:
        fail(f"{path}: 该步骤要求 isReady=false，但当前为 true")

    state = "可下载" if is_ready else "灰度中（客户端只提示，不提供下载）"
    print(
        f"OK  {path}: {version_name} (versionCode {version_code}, channel {channel}) —— {state}\n"
        f"    changelog: {changelog_path(path, version_code)}"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="校验 update/stable.json")
    parser.add_argument("manifest", nargs="?", default=None, help="清单路径，默认 update/stable.json")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--require-ready", action="store_true", help="要求 isReady=true 且 assets 完整")
    group.add_argument("--require-not-ready", action="store_true", help="要求 isReady=false")
    args = parser.parse_args()

    path = manifest_path(args.manifest)
    if not path.is_file():
        fail(f"清单不存在：{path}")

    try:
        manifest = load_manifest(path)
    except ValueError as error:
        fail(f"{path}: 不是合法 JSON —— {error}")

    validate(manifest, path, args.require_ready, args.require_not_ready)


if __name__ == "__main__":
    main()
