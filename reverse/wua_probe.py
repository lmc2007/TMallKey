#!/usr/bin/env python3
"""用 unidbg 产出的 wua 打 mtop，验证服务端是否接受（阶段 4）。

用法：
    # 只产 wua，不发请求
    python reverse/wua_probe.py --gen-only

    # 带真实会话打接口（cookies 从文件读，格式 `k=v; k2=v2`）
    python reverse/wua_probe.py --cookies cookies.txt \\
        --api mtop.tmall.campus.member.app.user.get

    # 对比：同一请求分别带 / 不带 x-mini-wua
    python reverse/wua_probe.py --cookies cookies.txt --api ... --compare

设计说明：
- wua 由 `mvn exec:java -Dexec.mainClass=com.tmall.WuaCli` 生成，结果在 `__WUA_JSON__ ` 行。
- mtop 的 h5 网关校验 sign（md5(token&t&appKey&data)），wua 是附加的反作弊头；
  两者分开，所以「sign 对 + wua 无效」和「sign 错」可以通过 ret 区分。
- 本脚本只读，不发任何写操作。
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HARNESS = os.path.join(ROOT, "reverse", "unidbg-harness")
sys.path.insert(0, ROOT)

from fktm.water.mtop import APP_KEY, GATEWAY, dumps, parse_h5_token, sign  # noqa: E402


def gen_wua(app_key: str = APP_KEY, flag: int = 8, ts: str | None = None) -> dict:
    """调 WuaCli 生成 wua，返回解析后的 JSON。"""
    ts = ts or str(int(time.time() * 1000))
    env = dict(os.environ)
    env["JAVA_HOME"] = env.get("JAVA_HOME", r"C:/Program Files/Java/jdk-21")
    env["MAVEN_OPTS"] = (
        "--add-opens java.base/java.util=ALL-UNNAMED "
        "--add-opens java.base/java.lang=ALL-UNNAMED"
    )
    mvn = shutil.which("mvn") or shutil.which("mvn.cmd") or "mvn.cmd"
    cmd = [
        mvn, "-s", "settings.xml", "-B", "-q", "exec:java",
        "-Dexec.mainClass=com.tmall.WuaCli",
        f"-Dexec.args={app_key} {flag} {ts}",
    ]
    proc = subprocess.run(cmd, cwd=HARNESS, env=env, capture_output=True, text=True, timeout=180)
    out = proc.stdout + proc.stderr
    for line in out.splitlines():
        if line.startswith("__WUA_JSON__ "):
            return json.loads(line[len("__WUA_JSON__ "):])
    raise RuntimeError("WuaCli 未输出结果：\n" + out[-2000:])


def read_cookies(path: str | None) -> dict:
    if not path:
        return {}
    raw = open(path, encoding="utf-8").read().strip()
    jar = {}
    for part in raw.split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            jar[k.strip()] = v.strip()
    return jar


def call_mtop(api: str, data: dict, cookies: dict, extra_headers: dict,
              ver: str = "1.0", timeout: float = 15.0) -> tuple[int, dict]:
    payload = dumps(data)
    ts = str(int(time.time() * 1000))
    token = parse_h5_token(cookies.get("_m_h5_tk"))
    query = urllib.parse.urlencode({
        "jsv": "2.7.2",
        "appKey": APP_KEY,
        "t": ts,
        "sign": sign(token, ts, APP_KEY, payload),
        "api": api,
        "v": ver,
        "type": "originaljson",
        "dataType": "json",
        "data": payload,
    })
    url = GATEWAY.format(api=api, ver=ver) + "?" + query
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
        "Referer": "https://share.confong.cn/",
        "Accept": "application/json",
        "Cookie": "; ".join(f"{k}={v}" for k, v in cookies.items()),
    }
    headers.update(extra_headers)
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8", "replace"))
    except Exception as e:  # noqa: BLE001
        return -1, {"ret": [f"NETWORK_ERROR::{e}"]}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="mtop.common.getTimestamp")
    ap.add_argument("--cookies", help="cookie 文件，格式 `k=v; k2=v2`")
    ap.add_argument("--flag", type=int, default=8)
    ap.add_argument("--gen-only", action="store_true", help="只生成 wua，不发请求")
    ap.add_argument("--compare", action="store_true", help="同一请求分别带 / 不带 x-mini-wua")
    args = ap.parse_args()

    print("[1/2] 生成 wua ...")
    got = gen_wua(flag=args.flag)
    if not got.get("ok"):
        print("生成失败:", got)
        return 1
    wua = got["wua"]
    print(f"      len={got['len']} initMs={got['initMs']} genMs={got['genMs']}")
    print(f"      {wua}")
    if args.gen_only:
        return 0

    cookies = read_cookies(args.cookies)
    print(f"[2/2] 请求 {args.api}（cookies {len(cookies)} 项）")

    variants = [("带 wua", {"x-mini-wua": wua})]
    if args.compare or not cookies:
        variants.append(("不带 wua", {}))
    for label, hdr in variants:
        status, body = call_mtop(args.api, {}, cookies, hdr)
        print(f"  [{label}] http={status} ret={body.get('ret')} api={body.get('api')}")
        if body.get("data"):
            print(f"           data={json.dumps(body['data'], ensure_ascii=False)[:300]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
