#!/usr/bin/env python3
"""Withdraw the expected GitHub Latest release and switch the stable manifest back."""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from update_manifest_common import DEFAULT_MANIFEST, load_manifest

SCRIPTS = Path(__file__).resolve().parent
TAG_PATTERN = re.compile(r"v[0-9]+\.[0-9]+\.[0-9]+")


def git(*args):
    return subprocess.check_output(["git", *args], encoding="utf-8").strip()


def github(endpoint, *args):
    return json.loads(subprocess.check_output(["gh", "api", endpoint, *args], encoding="utf-8"))


def manifest_history():
    path = DEFAULT_MANIFEST.as_posix()
    for commit in git("log", "--first-parent", "--format=%H", "--", path).splitlines():
        # A historical deletion has no manifest to restore.
        result = subprocess.run(["git", "show", f"{commit}:{path}"], capture_output=True, encoding="utf-8")
        if result.returncode == 0:
            yield json.loads(result.stdout)


def is_downloadable(manifest, published):
    tag = f"v{manifest.get('versionName')}"
    release = published.get(tag)
    assets = manifest.get("assets")
    if (manifest.get("isReady") is not True or not release or not assets
            or manifest.get("releaseUrl") != release["html_url"]):
        return False
    uploaded = {
        asset["browser_download_url"]: asset for asset in release["assets"] if asset["state"] == "uploaded"
    }
    for asset in assets:
        public_asset = uploaded.get(asset.get("url"))
        if not public_asset or public_asset["size"] != asset.get("size"):
            return False
        digest = public_asset.get("digest")
        if digest and digest != f"sha256:{asset.get('sha256')}":
            return False
    return True


def previous_manifest(history, releases, current):
    published = published_by_tag(releases)
    best = None
    for manifest in history:
        code = manifest.get("versionCode")
        if (type(code) is int and 0 < code < current["versionCode"]
                and (best is None or code > best["versionCode"])
                and is_downloadable(manifest, published)):
            best = manifest
    if best is None:
        raise ValueError("找不到仍公开且 APK 可下载的旧版，无法安全回退更新清单。")
    return best


def published_by_tag(releases):
    return {release["tag_name"]: release for release in releases if not release["draft"] and not release["prerelease"]}


def write_and_push(manifest, subject):
    DEFAULT_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    path = DEFAULT_MANIFEST.as_posix()
    subprocess.run([sys.executable, str(SCRIPTS / "validate-update-manifest.py"), path, "--require-ready"], check=True)
    git("-c", "user.name=github-actions[bot]", "-c", "user.email=github-actions[bot]@users.noreply.github.com",
        "commit", "-m", subject, "--", path)
    # Refuse a non-fast-forward push. A fresh run must inspect the new main first.
    git("push", "origin", "HEAD:refs/heads/main")


def output(name, value):
    if path := os.environ.get("GITHUB_OUTPUT"):
        with open(path, "a", encoding="utf-8") as stream:
            print(f"{name}={value}", file=stream)


def run(repo, tag, dry_run):
    if not TAG_PATTERN.fullmatch(tag):
        raise ValueError("目标 tag 必须形如 v2.1.12。")
    endpoint = f"repos/{repo}/releases"
    latest = github(f"{endpoint}/latest")
    if latest["tag_name"] != tag:
        raise ValueError(f"GitHub Latest 是 {latest['tag_name']}，与输入的 {tag} 不一致。")
    if latest.get("immutable"):
        raise ValueError(f"{tag} 的 Release 已启用不可变发布，GitHub 不允许将其转回草稿。")

    manifest = load_manifest(DEFAULT_MANIFEST)
    subject = f"release: 撤回 {tag}"
    retry = git("log", "-1", "--format=%s", "--", DEFAULT_MANIFEST.as_posix()) == subject
    if not retry and (manifest.get("isReady") is not True or f"v{manifest.get('versionName')}" != tag
                      or manifest.get("releaseUrl") != latest["html_url"]):
        raise ValueError("main 的正式更新清单与目标 Release 不一致，可能另有版本正在准备。")

    pages = github(f"{endpoint}?per_page=100", "--paginate", "--slurp")
    releases = [release for page in pages for release in page if release["tag_name"] != tag]
    if retry:
        if not is_downloadable(manifest, published_by_tag(releases)):
            raise ValueError("上次回退的版本已不可下载，无法继续撤回。")
        fallback = manifest
    else:
        fallback = previous_manifest(manifest_history(), releases, manifest)

    print(f"{'[预演] ' if dry_run else ''}撤回 {tag}，更新清单指向 v{fallback['versionName']}。", flush=True)
    if dry_run:
        return
    if not retry:
        write_and_push(fallback, subject)
    output("deploy", "true")

    # Update the exact release ID verified above, never whichever release happens to be latest now.
    updated = github(f"{endpoint}/{latest['id']}", "--method", "PATCH", "-F", "draft=true")
    if updated.get("draft") is not True:
        raise ValueError(f"GitHub 未将 {tag} 转为草稿。")
    print(f"{tag} 已转回草稿。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        run(os.environ["GITHUB_REPOSITORY"], args.tag, args.dry_run)
    except (ValueError, subprocess.CalledProcessError) as exc:
        raise SystemExit(f"::error::{exc}") from exc


if __name__ == "__main__":
    main()
