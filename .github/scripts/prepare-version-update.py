#!/usr/bin/env python3
"""Prepare Version Update 的核心脚本。

由 `.github/workflows/prepare-release.yml` 调用；也可以本地模拟：

    INPUT_VERSION_NAME=1.0.1 python3 .github/scripts/prepare-version-update.py

读取的环境变量（workflow 通过 INPUT_* 传入）：

    INPUT_VERSION_NAME     新 versionName，留空 = 当前补丁号 +1
    INPUT_VERSION_CODE     versionCode，留空 = 当前 + 1
    INPUT_CHANGELOG_MODE   auto | auto+extra | manual（默认 auto）
    INPUT_CHANGELOG_EXTRA  补充 / 手写的更新内容（多行；manual 模式必填）
    INPUT_RELEASE_NOTES    清单里的一句话简介（留空自动取第一条要点）
    INPUT_RELEASE_BRANCH   发布分支（默认 main），必须与当前分支一致
    INPUT_DRY_RUN          true/false，只预览不写文件、不提交

做三件事：

1. 改 `fktm-android/app/build.gradle.kts` 的 `versionCode` / `versionName`；
2. 新建 `update/changelogs/{versionCode}.changelog`；
3. 把 `update/stable.json` 切到新版本，并**重置为 isReady=false + assets 清空** ——
   此时客户端看到的是「灰度中」，只提示有新版本但不给下载，
   等 `Release APK` 把安装包传上去、清单改成 isReady=true 之后才真正可下载。

第 3 步里「先置为灰度中」是这个两阶段流程的关键：清单永远指向一个已经存在的
产物，不会出现「提示可更新、点下载 404」的窗口。
"""

import json
import os
import re
import subprocess
import sys
from pathlib import Path

from update_manifest_common import (
    CHANNEL_DEFAULT,
    canonical,
    changelog_path,
    load_manifest,
)

ROOT = Path(__file__).resolve().parents[2]
GRADLE_FILE = ROOT / "fktm-android" / "app" / "build.gradle.kts"
MANIFEST_FILE = ROOT / "update" / "stable.json"
CHANGELOG_DIR = ROOT / "update" / "changelogs"

VERSION_NAME_PATTERN = re.compile(r"^\d+\.\d+\.\d+$")
SECTION_ORDER = ["新增", "修复", "更改", "其他"]

# 只在 build.gradle.kts 里定位「行首缩进 + versionCode = 数字」，
# 避免误命中注释里提到的 versionCode 字样。
VERSION_CODE_PATTERN = re.compile(r"\n(\s*)versionCode\s*=\s*(\d+)")
VERSION_NAME_PATTERN_KTS = re.compile(r'\n(\s*)versionName\s*=\s*"([^"]*)"')


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def git(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=check,
    )


def env(name: str, default: str = "") -> str:
    return (os.environ.get(name) or default).strip()


def env_bool(name: str) -> bool:
    return env(name, "false").lower() == "true"


def current_branch() -> str:
    result = git("branch", "--show-current")
    if result.returncode != 0:
        fail("无法确定当前分支：git branch --show-current 失败")
    branch = result.stdout.strip()
    if not branch:
        fail("当前不在任何分支上（detached HEAD），无法继续")
    return branch


def read_versions() -> tuple:
    """从 build.gradle.kts 读当前 versionCode / versionName。"""
    text = GRADLE_FILE.read_text(encoding="utf-8")
    code_match = VERSION_CODE_PATTERN.search(text)
    name_match = VERSION_NAME_PATTERN_KTS.search(text)
    if not code_match or not name_match:
        fail(f"无法在 {GRADLE_FILE.relative_to(ROOT)} 中解析 versionCode / versionName")
    return int(code_match.group(2)), name_match.group(2)


def suggest_next_version_name(current: str) -> str:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", current)
    if not match:
        fail(f"当前 versionName {current} 不是 x.y.z 格式，无法自动递增；请手动填写 version_name")
    major, minor, patch = (int(part) for part in match.groups())
    return f"{major}.{minor}.{patch + 1}"


def remote_tag_exists(tag: str) -> bool:
    result = subprocess.run(
        ["git", "ls-remote", "--tags", "origin", f"refs/tags/{tag}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        fail(f"无法查询远端 tag {tag}：{result.stderr.strip() or 'git ls-remote 失败'}")
    return bool(result.stdout.strip())


def working_tree_clean() -> bool:
    return git("status", "--porcelain").stdout.strip() == ""


def resolve_target_branch() -> str:
    branch = env("INPUT_RELEASE_BRANCH", "main")
    if not branch:
        fail("无法确定发布分支，请填写 release_branch")
    return branch


def classify(subject: str):
    """把一条提交标题归到 changelog 的某一节；返回 None 表示这条不该进 changelog。"""
    match = re.match(r"^([A-Za-z]+)(\([^)]*\))?!?:\s*(.*)$", subject)
    if match:
        kind = match.group(1).lower()
        text = match.group(3).strip()
    else:
        kind = ""
        text = subject.strip()
    if not text:
        text = subject.strip()
    section = {
        "feat": "新增",
        "feature": "新增",
        "fix": "修复",
        "revert": "修复",
        "perf": "更改",
        "refactor": "更改",
        "docs": "更改",
        "chore": "其他",
    }.get(kind, "其他")
    if kind in ("release", "version"):
        return None
    return section, text


def collect_changelog() -> dict:
    """把上一次发版以来的提交整理成分节要点。

    基准 = 最近一条 `release: 发布 ...` 提交；找不到时回退为最近 40 条非 merge 提交。
    """
    base_result = subprocess.run(
        ["git", "log", "-1", "--format=%H", "--grep=^release: 发布", "HEAD"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    base = base_result.stdout.strip()
    if base:
        commits = git("log", f"{base}..HEAD", "--no-merges", "--format=%s").stdout
    else:
        commits = git("log", "-n", "40", "--no-merges", "--format=%s", "HEAD").stdout

    sections = {}
    for line in commits.splitlines():
        subject = line.strip()
        if not subject:
            continue
        classified = classify(subject)
        if classified is None:
            continue
        section, text = classified
        sections.setdefault(section, [])
        if text not in sections[section]:
            sections[section].append(text)
    return sections


def render_changelog(version_name: str, sections: dict) -> str:
    lines = [f"# v{version_name}", ""]
    for section in SECTION_ORDER:
        items = sections.get(section)
        if not items:
            continue
        lines.append(f"{section}：")
        lines.extend(f"- {item}" for item in items)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def first_bullet(sections: dict) -> str:
    for section in SECTION_ORDER:
        items = sections.get(section)
        if items:
            return items[0]
    return ""


def build_changelog(version_name: str, mode: str, extra: str) -> tuple:
    extra = extra.strip()
    if mode == "manual":
        if not extra:
            fail("changelog_mode=manual 时必须填写 changelog_extra")
        body = f"# v{version_name}\n\n{extra.strip()}\n"
        default_notes = extra.splitlines()[0].lstrip("-* ").strip()
        return body, default_notes

    sections = collect_changelog()
    if extra:
        for line in extra.splitlines():
            item = line.strip().lstrip("-* ").strip()
            if item:
                sections.setdefault("其他", [])
                if item not in sections["其他"]:
                    sections["其他"].append(item)

    if not any(sections.values()) and mode == "auto":
        fail(
            "auto 模式没有找到未发布的提交；请先提交代码，"
            "或改用 changelog_mode=auto+extra / manual 并填写 changelog_extra"
        )
    return render_changelog(version_name, sections), first_bullet(sections)


def already_prepared(version_code: int, version_name: str) -> bool:
    try:
        current_code, current_name = read_versions()
        manifest = load_manifest(MANIFEST_FILE)
        changelog = CHANGELOG_DIR / f"{version_code}.changelog"
        return (
            current_code == version_code
            and current_name == version_name
            and int(manifest.get("versionCode") or 0) == version_code
            and str(manifest.get("versionName", "")).strip() == version_name
            and changelog.is_file()
            and changelog.read_text(encoding="utf-8").strip() != ""
        )
    except Exception:
        return False


def write_github_output(key: str, value: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as out:
            out.write(f"{key}={value}\n")


def write_github_env(key: str, value: str) -> None:
    path = os.environ.get("GITHUB_ENV")
    if path:
        with open(path, "a", encoding="utf-8") as out:
            out.write(f"{key}={value}\n")


def main() -> None:
    version_name = env("INPUT_VERSION_NAME")
    version_code_raw = env("INPUT_VERSION_CODE")
    mode = env("INPUT_CHANGELOG_MODE", "auto") or "auto"
    extra = env("INPUT_CHANGELOG_EXTRA")
    release_notes = env("INPUT_RELEASE_NOTES")
    dry_run = env_bool("INPUT_DRY_RUN")

    if mode not in ("auto", "auto+extra", "manual"):
        fail(f"changelog_mode 只能是 auto / auto+extra / manual，当前为 {mode}")

    current_code, current_name = read_versions()
    if not version_name:
        version_name = suggest_next_version_name(current_name)
        print(f"未填写 version_name：当前 {current_name}，自动使用补丁号 +1 = {version_name}")
    if not VERSION_NAME_PATTERN.match(version_name):
        fail(f"versionName 格式必须是 x.y.z（例如 1.0.1），当前为 {version_name}")

    try:
        version_code = int(version_code_raw) if version_code_raw else current_code + 1
    except ValueError:
        fail(f"versionCode 必须是整数，当前输入 {version_code_raw}")

    if already_prepared(version_code, version_name):
        target_branch = resolve_target_branch()
        print(
            f"已准备过：{version_name} (versionCode {version_code}) 的版本文件与 changelog 均已就绪，"
            "无需重复准备，可直接运行 Release APK。"
        )
        write_github_output("version_code", str(version_code))
        write_github_output("version_name", version_name)
        write_github_output("target_branch", target_branch)
        write_github_output("already_prepared", "true")
        return

    if version_code <= current_code:
        fail(f"versionCode 必须大于当前值 {current_code}，当前输入 {version_code}")

    target_branch = resolve_target_branch()
    actual_branch = current_branch()
    if target_branch != actual_branch:
        fail(
            f"发布分支({target_branch}) 必须与当前分支({actual_branch}) 一致；"
            "如需其他分支，请先切到该分支再运行本 workflow"
        )

    tag = f"v{version_name}"
    if dry_run:
        print(f"[DRY RUN] 跳过远端 tag 检查（这一步需要网络）：{tag}")
    elif remote_tag_exists(tag):
        fail(f"远端 tag {tag} 已存在；请换更大的 versionCode / 换一个 versionName")

    changelog_file = CHANGELOG_DIR / f"{version_code}.changelog"
    if changelog_file.exists():
        fail(f"{changelog_file.relative_to(ROOT)} 已存在但版本号尚未更新到该值；请人工确认后处理（脚本不覆盖）")

    if not dry_run and not working_tree_clean():
        fail("工作区不干净；workflow 检出的仓库应为干净状态，请检查是否有并发运行")

    manifest = load_manifest(MANIFEST_FILE)
    if int(manifest.get("versionCode") or 0) != current_code:
        fail(
            f"清单 versionCode({manifest.get('versionCode')}) 与 build.gradle.kts({current_code}) 不一致；"
            "请先人工对齐再发版"
        )
    if manifest.get("isReady") is True:
        print(
            f"::notice::{MANIFEST_FILE.relative_to(ROOT)} 当前 isReady=true（上一版已发布），"
            "本次会把它重置为 isReady=false 并清空 assets —— 客户端随即显示「灰度中」，"
            "等 Release APK 跑完才恢复可下载。"
        )

    changelog_body, default_notes = build_changelog(version_name, mode, extra)
    final_release_notes = (release_notes or default_notes).strip()
    if not final_release_notes:
        fail("releaseNotes 为空；请填写 release_notes 或提供更新内容")

    gradle_text = GRADLE_FILE.read_text(encoding="utf-8")
    new_gradle_text = VERSION_CODE_PATTERN.sub(
        lambda m: f"\n{m.group(1)}versionCode = {version_code}", gradle_text, count=1
    )
    new_gradle_text = VERSION_NAME_PATTERN_KTS.sub(
        lambda m: f'\n{m.group(1)}versionName = "{version_name}"', new_gradle_text, count=1
    )
    if new_gradle_text == gradle_text:
        fail("未能定位并替换 build.gradle.kts 的版本号（文件格式可能已变化）")

    new_manifest = canonical(
        {
            **manifest,
            "channel": manifest.get("channel") or CHANNEL_DEFAULT,
            "versionName": version_name,
            "versionCode": version_code,
            "isReady": False,
            "releaseNotes": final_release_notes,
            "assets": [],
            "releaseUrl": None,
        }
    )
    manifest_text = json.dumps(new_manifest, ensure_ascii=False, indent=2) + "\n"

    print("=" * 72)
    print("[DRY RUN] 只预览：不写文件、不提交、不推送" if dry_run else "准备写入版本文件")
    print(f"当前版本: {current_name} (versionCode {current_code})")
    print(f"目标版本: {version_name} (versionCode {version_code})")
    print(f"发布分支: {target_branch}")
    print(f"tag:      {tag}")
    print(f"changelog 模式: {mode}")
    print(f"将提交信息: release: 准备 v{version_name}")
    print(f"releaseNotes: {final_release_notes}")
    print("-" * 72)
    print("changelog 预览:")
    print(changelog_body)
    print("-" * 72)
    print("将修改的文件:")
    print(f"  - {GRADLE_FILE.relative_to(ROOT)}")
    print(f"  - {MANIFEST_FILE.relative_to(ROOT)}（重置为 isReady=false，客户端显示「灰度中」）")
    print(f"  - 新建 {changelog_file.relative_to(ROOT)}")

    write_github_output("version_code", str(version_code))
    write_github_output("version_name", version_name)
    write_github_output("target_branch", target_branch)
    write_github_output("already_prepared", "false")

    if dry_run:
        print("\n[DRY RUN] 完成，未产生任何改动。")
        return

    GRADLE_FILE.write_text(new_gradle_text, encoding="utf-8")
    MANIFEST_FILE.write_text(manifest_text, encoding="utf-8")
    CHANGELOG_DIR.mkdir(parents=True, exist_ok=True)
    changelog_file.write_text(changelog_body, encoding="utf-8")
    write_github_env("VERSION_CODE", str(version_code))
    write_github_env("VERSION_NAME", version_name)
    write_github_env("TARGET_BRANCH", target_branch)
    print("\n版本文件已写入；后续步骤将提交推送。")


if __name__ == "__main__":
    main()
