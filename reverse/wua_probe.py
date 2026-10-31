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
        "utdid": os.environ.get("SG_UTDID", "apU98phzUu4DAOxRSTtO69oT"),
        "ttid": os.environ.get("SG_TTID", "600000@taobao_android_5.0.0"),
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
        # 按 Kaltsit 项目：必须装成 App 形态，否则直接进风控
        "User-Agent": os.environ.get("SG_UA", "MTOPSDK/3.1.1.7 (Android;16;OnePlus;PKR110)"),
        "x-appkey": APP_KEY,
        "x-pv": "6.2",
        "x-bx-version": "6.5.90",
        "x-app-ver": "5.0.0",
        "x-ttid": os.environ.get("SG_TTID", "600000@taobao_android_5.0.0"),
        "x-platform": "android",
        "Referer": "https://share.confong.cn/",
        "Accept": "application/json",
        "Cookie": "; ".join(f"{k}={v}" for k, v in cookies.items()),
    }
    headers.update(extra_headers)
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = json.loads(r.read().decode("utf-8", "replace"))
            body["__set_cookie"] = r.headers.get_all("Set-Cookie") or []
            return r.status, body
    except urllib.error.HTTPError as e:
        body = json.loads(e.read().decode("utf-8", "replace"))
        body["__set_cookie"] = e.headers.get_all("Set-Cookie") or []
        return e.code, body
    except Exception as e:  # noqa: BLE001
        return -1, {"ret": [f"NETWORK_ERROR::{e}"], "__set_cookie": []}


def absorb_h5_tokens(cookies: dict, set_cookies: list) -> bool:
    """从 Set-Cookie 里吸收 _m_h5_tk / _m_h5_tk_enc，返回是否有更新。"""
    changed = False
    for sc in set_cookies or []:
        first = sc.split(";", 1)[0]
        if "=" not in first:
            continue
        k, v = first.split("=", 1)
        k = k.strip()
        if k in ("_m_h5_tk", "_m_h5_tk_enc") and cookies.get(k) != v:
            cookies[k] = v
            changed = True
    return changed


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="mtop.common.getTimestamp")
    ap.add_argument("--cookies", help="cookie 文件，格式 `k=v; k2=v2`")
    ap.add_argument("--flag", type=int, default=8)
    ap.add_argument("--gen-only", action="store_true", help="只生成 wua，不发请求")
    ap.add_argument("--compare", action="store_true", help="同一请求分别带 / 不带 x-mini-wua")
    ap.add_argument("--data", default="{}", help="请求 data（JSON 字符串）")
    ap.add_argument("--ver", default="1.0")
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

    # h5 流程：先打一次拿 _m_h5_tk（首次通常 FAIL_SYS_TOKEN_EMPTY），再带 token 重签
    for attempt in range(3):
        st, bd = call_mtop(args.api, json.loads(args.data), cookies, {}, ver=args.ver)
        got_new = absorb_h5_tokens(cookies, bd.get("__set_cookie"))
        print(f"      取 token #{attempt + 1}: ret={bd.get('ret')} 新token={got_new}")
        if not got_new or "_m_h5_tk" in cookies and not str(bd.get("ret", [""])[0]).startswith("FAIL_SYS_TOKEN"):
            break
    print(f"      _m_h5_tk={cookies.get('_m_h5_tk')}")

    devid = os.environ.get("SG_DEVID", "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva")
    base_hdr = {"x-devid": devid}
    # Kaltsit 项目：CM 层还看这几个头（x-uid / x-sid / x-campus-id）
    if os.environ.get("SG_UID"):
        base_hdr["x-uid"] = os.environ["SG_UID"]
    if os.environ.get("SG_SID"):
        base_hdr["x-sid"] = os.environ["SG_SID"]
    if os.environ.get("SG_CAMPUS_ID"):
        base_hdr["x-campus-id"] = os.environ["SG_CAMPUS_ID"]
    if os.environ.get("SG_UMT"):
        base_hdr["x-umt"] = os.environ["SG_UMT"]
    if os.environ.get("SG_SGEXT"):
        base_hdr["x-sgext"] = os.environ["SG_SGEXT"]
    variants = [
        ("正常 wua", dict(base_hdr, **{"x-mini-wua": wua})),
        ("乱码 wua", dict(base_hdr, **{"x-mini-wua": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})),
    ]
    if args.compare or not cookies:
        variants.append(("不带 wua", base_hdr))
    for label, hdr in variants:
        status, body = call_mtop(args.api, json.loads(args.data), cookies, hdr, ver=args.ver)
        print(f"  [{label}] http={status} ret={body.get('ret')} api={body.get('api')}")
        if body.get("data"):
            print(f"           data={json.dumps(body['data'], ensure_ascii=False)[:300]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
