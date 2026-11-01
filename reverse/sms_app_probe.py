#!/usr/bin/env python3
"""smsSend 走 App 网关（/gw）的定性探针。

结论（2026-11-01 14:30，实测）：`/gw` 会**逐个点名**缺哪个协议头，
补齐 `m-pv → m-appkey → m-sign → m-t` 之后错误从
`FAIL_SYS_PROTOPARAM_MISSED` 前进到 `FAIL_SYS_REQUEST_EXPIRED`。
⇒ 协议骨架 = 这 4 个 `m-*` 头，外加 query 的 jsv/appKey/t/api/v/type/dataType。
剩下的「请求失效」= 服务端要用 m-sign 校验 m-t，占位符过不了 —— 必须填真签名。

用法:
    python sms_app_probe.py             # 协议骨架 + 自动补齐演示
    python sms_app_probe.py --signed    # 用 unidbg 真签名填 m-sign（会起 mvn，慢）
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.parse

from mtop_app_probe import (APP_KEY, DEVID, TTID, UA, UTDID, build_headers,
                            send, sign_via_unidbg)

API = "mtop.taobao.mloginService.smsSend"
VER = "1.0"

POOL = {
    "m-pv": "6.2", "m-appkey": APP_KEY, "m-sign": "", "m-t": "0",
    "m-api": API, "m-v": VER, "m-ttid": TTID, "m-utdid": UTDID, "m-devid": DEVID,
    "m-usid": "", "m-sid": "", "m-h5": "false", "m-features": "27",
    "m-nettype": "WIFI", "m-app-ver": "5.0.0", "m-os": "android",
}


def envelope(phone: str) -> str:
    """复刻 com.ali.user.mobile.data.LoginComponent 的三段式参数。"""
    now = int(time.time() * 1000)
    ext = {"apiVersion": "2.0", "deviceName": "PKR110", "sdkTraceId": str(now)}
    login = {
        "loginId": phone, "loginType": "taobao", "site": 0, "locale": "zh_CN",
        "countryCode": "86", "phoneCode": "86", "codeLength": "6", "t": now,
    }
    risk = {
        "osName": "android", "osVersion": "16", "deviceModel": "PKR110",
        "deviceBrand": "OnePlus", "deviceName": "PKR110",
        "screenSize": "1080x2400", "t": str(now),
    }
    return json.dumps({
        "ext": json.dumps(ext, separators=(",", ":")),
        "loginInfo": json.dumps(login, separators=(",", ":")),
        "riskControlInfo": json.dumps(risk, separators=(",", ":")),
    }, separators=(",", ":"))


def build(ts: str, data: str) -> str:
    qs = {
        "jsv": "2.6.1", "appKey": APP_KEY, "t": ts, "api": API, "v": VER,
        "type": "originaljson", "dataType": "json", "x-features": "27",
    }
    return f"https://acs.m.taobao.com/gw/{API}/{VER}/?" + urllib.parse.urlencode(qs)


def ret_of(txt: str) -> str:
    m = re.search(r'"ret":\["(.*?)"\]', txt.replace("\n", "").replace("  ", ""))
    return m.group(1) if m else txt[:160].replace("\n", "")


def auto_fill(url: str, data: str, headers: dict, rounds: int = 12) -> dict:
    """按服务端点名逐个补 m-* 头，直到不再提示缺项。"""
    h = dict(headers)
    body = urllib.parse.urlencode({"data": data}).encode()
    for i in range(rounds):
        st, txt = send(url, body, h)
        ret = ret_of(txt)
        print(f"  r{i:02d} n={len(h)} {ret}")
        miss = re.search(r"缺少协议\S*?(m-[\w-]+)", ret)
        if not miss:
            return h
        key = miss.group(1)
        if key in h:
            print("  ! 已补过仍报缺，停止")
            return h
        h[key] = POOL.get(key, "x")
    return h


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--phone", default="")
    ap.add_argument("--signed", action="store_true")
    a = ap.parse_args()

    data = envelope(a.phone) if a.phone else "{}"
    ts = str(int(time.time() * 1000))
    url = build(ts, data)

    if not a.signed:
        print("[*] 协议骨架自动补齐：")
        auto_fill(url, data, {"User-Agent": UA})
        return 0

    print("[*] unidbg 产签名（约 30-60s）…")
    sig = sign_via_unidbg(API, VER, data, ts, DEVID, UTDID, TTID)
    print(f"[+] x-sign={sig.get('x-sign')} wuaLen={len(sig.get('x-mini-wua') or '')}")

    h = build_headers(sig)
    h["m-pv"] = "6.2"
    h["m-appkey"] = APP_KEY
    h["m-t"] = ts
    h["m-sign"] = sig.get("x-sign", "")
    st, txt = send(url, urllib.parse.urlencode({"data": data}).encode(), h)
    print(f"[signed] {st} {ret_of(txt)}")

    # 变体：query 里也放一份 x-sign / sign
    for label, extra in (
        ("query x-sign", {"x-sign": sig.get("x-sign", "")}),
        ("query sign", {"sign": sig.get("x-sign", "")}),
    ):
        q = dict(urllib.parse.parse_qsl(url.split("?", 1)[1]))
        q.update(extra)
        u2 = f"https://acs.m.taobao.com/gw/{API}/{VER}/?" + urllib.parse.urlencode(q)
        st, txt = send(u2, urllib.parse.urlencode({"data": data}).encode(), h)
        print(f"[{label}] {st} {ret_of(txt)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
