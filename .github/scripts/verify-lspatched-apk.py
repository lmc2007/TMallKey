#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""verify-lspatched-apk.py —— 补丁版天猫校园的产物校验

发版流水线里 LSPatch 打完包之后必须过这一关。它检查的不是「文件存在」，
而是**这个包能不能装、装上去还能不能覆盖升级、里面的原包是不是我们要的那个**：

  1. config.json 的签名伪装必须是 `-l 2`（sigBypassLevel=2）
     —— 少了它，阿里安全 SDK 查签名后直接闪退（v0.6 的坑）；
  2. `assets/lspatch/origin.apk` 的 sha256 必须等于输入的原包
     —— LSPatch 走 nested apk，打错原包的话外面完全看不出来；
  3. `assets/lspatch/modules/com.fktm.tmxydump.apk` 必须等于刚编出来的模块
     —— 不然装上去的是上一次的旧模块，症状是「改了代码没生效」；
  4. 包名 / versionCode 必须和原包一致（overrideVersionCode=false）
     —— 变了就意味着用户要卸载重装、丢登录态；
  5. APK 签名证书指纹必须等于钉住的那一个
     —— **这条决定老用户能否覆盖安装**：LSPatch 用内置 keystore，
       换过 keystore 指纹就变，用户必须卸载才能装，登录态全丢。

用法：
  verify-lspatched-apk.py <patched.apk> <original.apk> <module.apk>
                          [--expect-cert SHA256] [--expect-package com.tmall.campus.and]

apksigner / aapt 从 --apksigner / --aapt 取，没给就从 $ANDROID_HOME/build-tools 里挑最新的。
缺工具时只跳过对应检查并告警（本地跑不装 SDK 也能验结构）。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

MODULE_ENTRY = "assets/lspatch/modules/com.fktm.tmxydump.apk"
ORIGIN_ENTRY = "assets/lspatch/origin.apk"
CONFIG_ENTRY = "assets/lspatch/config.json"
LOADER_ENTRY = "assets/lspatch/loader.dex"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

failures: list[str] = []
warnings: list[str] = []


def ok(msg: str) -> None:
    print(f"  \033[32m✓\033[0m {msg}")


def bad(msg: str) -> None:
    failures.append(msg)
    print(f"  \033[31m✗\033[0m {msg}")


def warn(msg: str) -> None:
    warnings.append(msg)
    print(f"  \033[33m!\033[0m {msg}")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_entry(apk: Path, name: str) -> bytes:
    """按本地头偏移直接读一个 zip 条目。

    不能用 zipfile.read()：LSPatch 把 121 MB 的 origin.apk 以 stored 方式写在
    已有条目之后，偏移在 Python 看来是「重叠」的，会直接抛 BadZipFile
    （"possible zip bomb"）。这里是自己的产物，按偏移老老实实读就行。
    """
    import struct
    import zlib

    with zipfile.ZipFile(apk) as zf:
        info = zf.getinfo(name)
    with apk.open("rb") as fh:
        fh.seek(info.header_offset)
        header = fh.read(30)
        if header[:4] != b"PK\x03\x04":
            raise RuntimeError(f"{name} 的本地头不是 PK\\x03\\x04")
        name_len, extra_len = struct.unpack("<HH", header[26:30])
        fh.seek(info.header_offset + 30 + name_len + extra_len)
        raw = fh.read(info.compress_size)
    if info.compress_type == zipfile.ZIP_STORED:
        return raw
    if info.compress_type == zipfile.ZIP_DEFLATED:
        return zlib.decompress(raw, -15)
    raise RuntimeError(f"{name} 用了不支持的压缩方式 {info.compress_type}")


def find_build_tool(name: str, explicit: str | None) -> str | None:
    if explicit:
        return explicit
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        return None
    tools = Path(sdk) / "build-tools"
    if not tools.is_dir():
        return None
    exts = (".exe", ".bat", ".cmd", "") if os.name == "nt" else ("",)
    for ver in sorted((d for d in tools.iterdir() if d.is_dir()), reverse=True):
        for ext in exts:
            cand = ver / f"{name}{ext}"
            if cand.is_file():
                return str(cand)
    return None


def run(cmd: list[str]) -> str:
    # Windows 上 apksigner/aapt 是 .bat，必须经 cmd.exe 才能起（且依赖 JAVA_HOME）。
    # CI（ubuntu）里它们是普通脚本，走不到这一支。
    if os.name == "nt" and cmd[0].lower().endswith((".bat", ".cmd")):
        cmd = [os.environ.get("COMSPEC", "cmd.exe"), "/c", *cmd]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} 失败：{(proc.stderr or proc.stdout).strip()[:400]}")
    return proc.stdout


def check_structure(apk: Path, original: Path, module: Path) -> None:
    print("· 结构")
    with zipfile.ZipFile(apk) as z:
        names = set(z.namelist())

        if LOADER_ENTRY not in names:
            bad(f"缺 {LOADER_ENTRY}")
        else:
            ok("loader.dex 存在")

        missing = [abi for abi in ABIS if f"assets/lspatch/so/{abi}/liblspatch.so" not in names]
        if missing:
            bad(f"缺 ABI 的 liblspatch.so：{', '.join(missing)}")
        else:
            ok("4 个 ABI 的 liblspatch.so 齐全")

        if CONFIG_ENTRY not in names:
            bad(f"缺 {CONFIG_ENTRY}")
        else:
            cfg = json.loads(read_entry(apk, CONFIG_ENTRY))
            lv = cfg.get("sigBypassLevel")
            if lv != 2:
                bad(f"sigBypassLevel={lv}，必须是 2（-l 2 / pm+openat 双重伪装）")
            else:
                ok("sigBypassLevel = 2（签名伪装 pm+openat）")
            if cfg.get("useManager"):
                bad("useManager=true：这种包装到设备上要配管理器，不能直接发包")
            else:
                ok("useManager = false（模块已内嵌，装完即生效）")
            if cfg.get("overrideVersionCode"):
                bad("overrideVersionCode=true：versionCode 被改成 1，用户无法覆盖升级")
            else:
                ok("overrideVersionCode = false（versionCode 保持原值）")
            if cfg.get("debuggable"):
                warn("debuggable=true：这是调试包，不要发")
            if not cfg.get("originalSignature"):
                bad("originalSignature 为空：签名伪装没有原签名可喂，SDK 侧会失败")
            else:
                ok("originalSignature 已内嵌")
            ver = (cfg.get("lspConfig") or {}).get("VERSION_NAME")
            print(f"    LSPatch 版本：{ver}")

        if ORIGIN_ENTRY not in names:
            bad(f"缺 {ORIGIN_ENTRY}（nested apk）")
        else:
            want = sha256_file(original)
            got = sha256_bytes(read_entry(apk, ORIGIN_ENTRY))
            if want != got:
                bad(f"内嵌原包不是输入的原包（期望 {want[:12]}…，实际 {got[:12]}…）")
            else:
                ok(f"内嵌原包 == 输入原包（{want[:12]}…）")

        if MODULE_ENTRY not in names:
            bad(f"缺 {MODULE_ENTRY}")
        else:
            want = sha256_file(module)
            got = sha256_bytes(read_entry(apk, MODULE_ENTRY))
            if want != got:
                bad(f"内嵌模块不等于本次构建的模块（期望 {want[:12]}…，实际 {got[:12]}…）")
            else:
                ok(f"内嵌模块 == 本次构建（{want[:12]}…）")

        # 9 个 dex 的原包会被 LSPatch 收进 origin.apk，外层只留 loader
        dex = [n for n in names if re.fullmatch(r"classes\d*\.dex", n)]
        if len(dex) > 1:
            warn(f"外层还有 {len(dex)} 个 dex，nested apk 模式下通常只剩 classes.dex")


def check_manifest(apk: Path, original: Path, package: str, aapt: str | None) -> None:
    print("· 包名 / 版本")
    if not aapt:
        warn("找不到 aapt，跳过包名/版本校验")
        return
    pat = re.compile(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")

    def badging(p: Path):
        out = run([aapt, "dump", "badging", str(p)])
        m = pat.search(out)
        if not m:
            raise RuntimeError(f"无法解析 {p} 的 badging")
        return m.group(1), int(m.group(2)), m.group(3)

    try:
        pkg, code, name = badging(apk)
        opkg, ocode, oname = badging(original)
    except RuntimeError as exc:
        bad(f"读取 manifest 失败：{exc}")
        return
    if pkg != package:
        bad(f"包名是 {pkg}，期望 {package}")
    elif pkg != opkg:
        bad(f"包名与原包不一致：{pkg} vs {opkg}")
    else:
        ok(f"包名 {pkg}")
    if (code, name) != (ocode, oname):
        bad(f"版本被改动：{name}({code}) vs 原包 {oname}({ocode})")
    else:
        ok(f"版本沿用原包 {name} (versionCode {code})")


def check_signature(apk: Path, expect: str | None, apksigner: str | None) -> None:
    print("· 签名")
    if not apksigner:
        warn("找不到 apksigner，跳过签名校验")
        return
    try:
        run([apksigner, "verify", str(apk)])
    except RuntimeError as exc:
        bad(f"签名校验不通过：{exc}")
        return
    out = run([apksigner, "verify", "--print-certs", str(apk)])
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", out)
    if not m:
        bad("读不到签名证书指纹")
        return
    got = m.group(1).lower()
    ok(f"签名有效，证书 {got[:16]}…")
    if expect:
        if got != expect.lower():
            bad(
                "证书指纹与钉住的不一致："
                f"期望 {expect[:16]}…，实际 {got[:16]}…\n"
                "    → LSPatch keystore 换过了，老用户无法覆盖安装，必须人工确认"
            )
        else:
            ok("证书指纹与钉住值一致（老用户可覆盖安装）")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("patched", type=Path)
    ap.add_argument("original", type=Path)
    ap.add_argument("module", type=Path)
    ap.add_argument("--expect-cert", default=None, help="LSPatch 签名证书 SHA-256")
    ap.add_argument("--expect-package", default="com.tmall.campus.and")
    ap.add_argument("--apksigner", default=None)
    ap.add_argument("--aapt", default=None)
    args = ap.parse_args()

    for p in (args.patched, args.original, args.module):
        if not p.is_file():
            print(f"找不到文件：{p}", file=sys.stderr)
            return 2

    print(f"补丁包：{args.patched}（{args.patched.stat().st_size} 字节）")
    check_structure(args.patched, args.original, args.module)
    check_manifest(
        args.patched,
        args.original,
        args.expect_package,
        # 只认 aapt：aapt2 没有 `dump badging` 子命令
        find_build_tool("aapt", args.aapt),
    )
    check_signature(args.patched, args.expect_cert, find_build_tool("apksigner", args.apksigner))

    print()
    if failures:
        print(f"\033[31m失败 {len(failures)} 项\033[0m")
        for f in failures:
            print(f"  - {f.splitlines()[0]}")
        return 1
    if warnings:
        print(f"\033[33m通过，但有 {len(warnings)} 条告警\033[0m")
    else:
        print("\033[32m全部通过\033[0m")
    return 0


if __name__ == "__main__":
    sys.exit(main())
