#!/usr/bin/env python3
"""h5 网关探测：appKey=12574478 + _m_h5_tk 令牌链，data 带 loginInfo/riskControlInfo。

用途：验证 smsSend 在 h5 网关下是否也报 PROTOPARAM_MISSED（区分"接口要求" vs "网关要求"）。
"""
from __future__ import annotations

import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

APPKEY = "12574478"
UA = ("Mozilla/5.0 (Linux; Android 16; PKR110 Build/AP3A.240617.008; wv) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36")
COOKIES: dict[str, str] = {}


def sign(token: str, t: str, data: str) -> str:
    return hashlib.md5(f"{token}&{t}&{APPKEY}&{data}".encode()).hexdigest()


def call(api: str, data: str, ver: str = "1.0") -> str:
    t = str(int(time.time() * 1000))
    token = COOKIES.get("_m_h5_tk", "").split("_")[0]
    qs = urllib.parse.urlencode({
        "jsv": "2.7.2", "appKey": APPKEY, "t": t, "sign": sign(token, t, data),
        "api": api, "v": ver, "type": "originaljson", "dataType": "json", "data": data,
    })
    url = f"https://h5api.m.taobao.com/h5/{api}/{ver}/?{qs}"
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Referer": "https://login.taobao.com/",
        "Cookie": "; ".join(f"{k}={v}" for k, v in COOKIES.items()),
        "Accept": "application/json",
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            body, sc = r.read().decode("utf-8", "replace"), r.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as e:
        body, sc = e.read().decode("utf-8", "replace"), e.headers.get_all("Set-Cookie") or []
    for c in sc:
        first = c.split(";", 1)[0]
        if "=" in first:
            k, v = first.split("=", 1)
            if k.strip() in ("_m_h5_tk", "_m_h5_tk_enc"):
                COOKIES[k.strip()] = v
    return body


def main() -> int:
    api = sys.argv[1] if len(sys.argv) > 1 else "mtop.taobao.mloginService.smsSend"
    path = sys.argv[2] if len(sys.argv) > 2 else None
    data = open(path, encoding="utf-8").read() if path else "{}"
    print("[1]", call(api, data)[:300])
    print("[2]", call(api, data)[:500])
    return 0


if __name__ == "__main__":
    sys.exit(main())
