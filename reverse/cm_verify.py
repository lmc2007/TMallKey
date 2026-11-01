#!/usr/bin/env python3
"""CM 层状态验证：同一个 cookie2 会话，分别用 h5 网关和 App 网关打校园接口。

目的（PLAN-reverse 阶段 4 的产品侧验收）：
  1. 确认当前会话是 CM✅ 还是 CM❌；
  2. 用 unidbg 自产的 x-sign + 长 x-mini-wua（App 形态）打同一个接口，
     看服务端是否下发新的 cookie2（= 把会话升级成 CM✅）。

用法:
    # h5 网关（12574478 + md5 sign），看会话当前 CM 状态
    python reverse/cm_verify.py --cookies .tmp/cookies.txt \
        --api mtop.tmall.campus.member.app.user.get --modes h5

    # App 网关（34270422 + unidbg x-sign + 261 长 wua）
    python reverse/cm_verify.py --cookies .tmp/cookies.txt \
        --api mtop.tmall.campus.member.app.user.get --modes gw

    # 两个都打，并对比打之前/之后的 cookie2
    python reverse/cm_verify.py --cookies .tmp/cookies.txt --modes h5,gw
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from mtop_app_probe import (  # noqa: E402
    APP_KEY as APP_KEY_APP, DEVID, TTID, UTDID, UMT,
    build_headers, sign_via_unidbg,
)

APP_KEY_H5 = "12574478"
GW_H5 = "https://h5api.m.taobao.com/h5/{api}/{ver}/"
GW_APP = "https://guide-acs.m.taobao.com/gw/{api}/{ver}/"
API_CM_USER = "mtop.tmall.campus.member.app.user.get"


def read_cookies(path: str) -> dict:
    raw = open(path, encoding="utf-8").read().strip()
    jar = {}
    for part in raw.replace("\n", ";").split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            jar[k.strip()] = v.strip()
    return jar


def cookie_header(jar: dict) -> str:
    return "; ".join(f"{k}={v}" for k, v in jar.items())


def digest(v: str | None) -> str:
    """只输出指纹，不落盘明文。"""
    if not v:
        return "-"
    return f"len{len(v)}/{hashlib.md5(v.encode()).hexdigest()[:8]}"


def parse_h5_token(v: str | None) -> str:
    if not v:
        return ""
    return v.split("_")[0]


def h5_sign(token: str, t: str, data: str, app_key: str = APP_KEY_H5) -> str:
    return hashlib.md5(f"{token}&{t}&{app_key}&{data}".encode()).hexdigest()


def http(url: str, body: bytes | None, headers: dict, timeout: float = 15.0):
    req = urllib.request.Request(url, data=body, headers=headers,
                                 method="POST" if body is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace"), r.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), e.headers.get_all("Set-Cookie") or []
    except Exception as e:  # noqa: BLE001
        return -1, f"NETWORK_ERROR::{e}", []


def absorb(jar: dict, set_cookies: list[str]) -> dict:
    """吸收 Set-Cookie，返回发生变化的键（不打印值）。"""
    changed = {}
    for sc in set_cookies:
        first = sc.split(";", 1)[0]
        if "=" not in first:
            continue
        k, v = first.split("=", 1)
        k, v = k.strip(), v.strip()
        if k in ("cookie2", "cookie17", "_l_g_", "_m_h5_tk", "_m_h5_tk_enc", "sgcookie", "unb", "_nk_"):
            if jar.get(k) != v:
                changed[k] = (digest(jar.get(k)), digest(v))
                jar[k] = v
    return changed


def call_h5(api: str, ver: str, jar: dict, data: dict) -> dict:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    ts = str(int(time.time() * 1000))
    url_base = GW_H5.format(api=api, ver=ver)
    last = {}
    # 首次通常 FAIL_SYS_TOKEN_EMPTY，服务端会下发 _m_h5_tk
    for attempt in range(3):
        token = parse_h5_token(jar.get("_m_h5_tk"))
        query = urllib.parse.urlencode({
            "jsv": "2.7.2", "appKey": APP_KEY_H5, "t": ts,
            "sign": h5_sign(token, ts, payload), "api": api, "v": ver,
            "type": "originaljson", "dataType": "json", "data": payload,
        })
        headers = {
            "User-Agent": "MTOPSDK/3.1.1.7 (Android;16;OnePlus;PKR110)",
            "x-appkey": APP_KEY_H5, "x-pv": "6.2", "x-app-ver": "5.0.0",
            "x-ttid": TTID, "x-utdid": UTDID, "x-devid": DEVID,
            "x-platform": "android", "Accept": "application/json",
            "Referer": "https://share.confong.cn/",
            "Cookie": cookie_header(jar),
        }
        st, txt, sc = http(url_base + "?" + query, None, headers)
        changed = absorb(jar, sc)
        last = {"http": st, "txt": txt, "changed": changed, "mode": "h5", "attempt": attempt + 1}
        ret = ""
        try:
            ret = str((json.loads(txt).get("ret") or [""])[0])
        except Exception:  # noqa: BLE001
            pass
        if "_m_h5_tk" in changed and attempt < 2 and ret.startswith("FAIL_SYS_TOKEN"):
            continue
        if ret.startswith("FAIL_SYS_TOKEN") and attempt < 2:
            continue
        break
    return last


def call_gw(api: str, ver: str, jar: dict, data: dict, wuaflag: int,
            ts: str | None = None, variant: str = "a") -> dict:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    ts = ts or str(int(time.time() * 1000))
    sig = sign_via_unidbg(api, ver, payload, ts, DEVID, UTDID, TTID)
    headers = build_headers(sig)
    headers["x-devid"] = DEVID
    headers["Cookie"] = cookie_header(jar)
    for env_key, hdr in (("SG_UID", "x-uid"), ("SG_SID", "x-sid"), ("SG_CAMPUS_ID", "x-campus-id")):
        if os.environ.get(env_key):
            headers[hdr] = os.environ[env_key]
    pq = {"jsv": "2.6.1", "appKey": APP_KEY_APP, "t": ts, "api": api, "v": ver,
          "type": "originaljson", "dataType": "json", "x-features": "27"}
    if variant in ("b", "c"):
        pq["H5Request"] = "false"
    qs = urllib.parse.urlencode(pq)
    url = GW_APP.format(api=api, ver=ver) + "?" + qs
    body = urllib.parse.urlencode({"data": payload}).encode()
    if variant == "c":
        url = url + "&" + urllib.parse.urlencode({"data": payload})
        body = None
    if variant == "d":
        headers = {k: v for k, v in headers.items() if k not in ("x-mini-wua", "x-umt", "x-sgext")}
    st, txt, sc = http(url, body, headers)
    changed = absorb(jar, sc)
    return {"http": st, "txt": txt, "changed": changed, "mode": "gw",
            "sign": sig.get("x-sign"), "wua_len": len(sig.get("x-mini-wua") or ""),
            "input": sig.get("input")}


def show(r: dict) -> None:
    try:
        b = json.loads(r["txt"])
        ret = b.get("ret")
        data = b.get("data")
        api = b.get("api")
        v = b.get("v")
    except Exception:  # noqa: BLE001
        print(f"    http={r['http']} RAW {r['txt'][:300]}")
        return
    print(f"    http={r['http']} api={api} v={v} ret={ret}")
    if data is not None:
        s = json.dumps(data, ensure_ascii=False)
        print(f"    data={s[:600]}")
    if r.get("changed"):
        print(f"    Set-Cookie 变更: {r['changed']}")
    else:
        print("    Set-Cookie 无关键变更")
    if r.get("mode") == "gw":
        print(f"    x-sign={r.get('sign')} wua_len={r.get('wua_len')}")


def verdict(r: dict) -> str:
    try:
        ret = str((json.loads(r["txt"]).get("ret") or [""])[0])
    except Exception:  # noqa: BLE001
        return "未知"
    if ret.startswith("SUCCESS"):
        return "CM✅（接口放行）"
    if "CE003" in ret or "USER_NOT_LOGIN" in ret or "SESSION_EXPIRED" in ret:
        return "会话本身失效"
    if "CAMPUS" in ret.upper() or "BIZ" in ret.upper():
        return "CM❌（业务层拒绝）"
    return ret


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cookies", required=True)
    ap.add_argument("--api", default=API_CM_USER)
    ap.add_argument("--ver", default="1.0")
    ap.add_argument("--data", default="{}")
    ap.add_argument("--modes", default="h5")
    ap.add_argument("--wuaflag", type=int, default=4)
    ap.add_argument("--t", default=None, help="固定时间戳（便于复用签名缓存）")
    ap.add_argument("--variant", default="a", help="a=POST body / b=+H5Request / c=GET query / d=去掉 SG 头")
    a = ap.parse_args()

    jar = read_cookies(a.cookies)
    data = json.loads(a.data)
    print(f"[in ] cookies={len(jar)} cookie2={digest(jar.get('cookie2'))} "
          f"unb={jar.get('unb','-')}  api={a.api}")
    before = dict(jar)

    for mode in [m.strip() for m in a.modes.split(",") if m.strip()]:
        print(f"\n== mode={mode} ==")
        if mode == "h5":
            r = call_h5(a.api, a.ver, jar, data)
        elif mode == "gw":
            os.environ.setdefault("SG_WUAFLAG", str(a.wuaflag))
            r = call_gw(a.api, a.ver, jar, data, a.wuaflag, a.t, a.variant)
        else:
            print("    未知 mode")
            continue
        show(r)
        print(f"    判定: {verdict(r)}")

    after = jar
    diff = {k: (digest(before.get(k)), digest(after.get(k)))
            for k in set(before) | set(after) if before.get(k) != after.get(k)}
    print(f"\n[out] cookie2={digest(after.get('cookie2'))}")
    print(f"[out] 会话变化: {diff if diff else '无'}")
    # 把最新 cookie 落盘，供下一次继续验证
    out = a.cookies + ".new"
    with open(out, "w", encoding="utf-8") as f:
        f.write(cookie_header(after))
    print(f"[out] 最新 cookie 已写 {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
