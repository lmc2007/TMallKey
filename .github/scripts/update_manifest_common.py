"""`update/stable.json` 的读写与规范化。

三个脚本共用（prepare / write-published / validate）：
清单的字段顺序、changelog 路径、以及「一句话简介」的兜底规则集中在这里，
免得三处各写一份、发版几次之后慢慢跑偏。
"""

import json
from pathlib import Path

DEFAULT_MANIFEST = Path("update") / "stable.json"
CHANNEL_DEFAULT = "stable"
CHANGELOG_DIR_NAME = "changelogs"

# 清单字段的规范顺序 —— 写回时按这个顺序排，diff 才稳定、可读。
CANONICAL_KEYS = (
    "channel",
    "versionName",
    "versionCode",
    "isReady",
    "minVersionCode",
    "releaseNotes",
    "releaseUrl",
    "assets",
)


def manifest_path(argv_path=None) -> Path:
    return Path(argv_path) if argv_path else DEFAULT_MANIFEST


def load_manifest(path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def canonical(manifest: dict) -> dict:
    """按规范顺序整理字段；未知字段原样保留在末尾，不做丢弃。"""
    out = {}
    for key in CANONICAL_KEYS:
        if key == "channel":
            out[key] = str(manifest.get("channel") or CHANNEL_DEFAULT).strip() or CHANNEL_DEFAULT
        elif key == "versionName":
            out[key] = str(manifest.get("versionName", "")).strip()
        elif key == "versionCode":
            out[key] = int(manifest.get("versionCode") or 0)
        elif key == "isReady":
            out[key] = bool(manifest.get("isReady", False))
        elif key == "minVersionCode":
            if manifest.get("minVersionCode") is not None:
                out[key] = int(manifest["minVersionCode"])
        elif key == "releaseNotes":
            notes = str(manifest.get("releaseNotes") or "").strip()
            if notes:
                out[key] = notes
        elif key == "releaseUrl":
            url = str(manifest.get("releaseUrl") or "").strip()
            if url:
                out[key] = url
        elif key == "assets":
            out[key] = list(manifest.get("assets") or [])

    # 已知字段在上一轮已经处理过（空值会被有意丢弃）；这里只补未知的扩展字段，
    # 不能按「不在 out 里」判断 —— 那会把刚被丢弃的 releaseUrl 之类又捡回来。
    for key, value in manifest.items():
        if key not in CANONICAL_KEYS:
            out[key] = value
    return out


def dumps(manifest: dict) -> str:
    return json.dumps(canonical(manifest), ensure_ascii=False, indent=2) + "\n"


def dump_manifest(manifest: dict, path) -> str:
    text = dumps(manifest)
    Path(path).write_text(text, encoding="utf-8")
    return text


def changelog_path(manifest_file, version_code) -> Path:
    return Path(manifest_file).parent / CHANGELOG_DIR_NAME / f"{version_code}.changelog"


def read_changelog(manifest_file, version_code):
    """返回 (文本 or None, 路径)。文件不存在时文本为 None，方便调用方给准确报错。"""
    path = changelog_path(manifest_file, version_code)
    if not path.is_file():
        return None, path
    return path.read_text(encoding="utf-8"), path


def first_bullet(text: str) -> str:
    """取 changelog 的第一条要点，用作清单里一句话简介的兜底。"""
    for raw in (text or "").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        line = line.lstrip("-*").strip()
        if line:
            return line
    return ""
