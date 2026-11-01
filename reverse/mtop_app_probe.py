#!/usr/bin/env python3
"""App 形态 mtop 请求探测：x-sign 由本地 unidbg(SignCli) 产，不需要真机参与。

和 wua_probe.py 的区别：那个走 h5 的 `sign=`（md5 token 链），这个走 App 的 `x-sign`。
用途：验证 x-sign 能不能让服务端放行（smsSend 之前卡在 FAIL_SYS_PROTOPARAM_MISSED）。

用法:
    python mtop_app_probe.py --api=mtop.taobao.mloginService.smsSend --data='{...}'
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

APP_KEY = os.environ.get("SG_APPKEY", "34270422")
UTDID = os.environ.get("SG_UTDID", "apU98phzUu4DAOxRSTtO69oT")
TTID = os.environ.get("SG_TTID", "600000@taobao_android_5.0.0")
DEVID = os.environ.get("SG_DEVID", "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva")
UMT = os.environ.get("SG_UMT", "Ad0BH7lLPK3hjwKhkWe7IB7ZmRBUoiEo")
UA = os.environ.get("SG_UA", "MTOPSDK/3.1.1.7 (Android;16;OnePlus;PKR110)")

HARNESS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "unidbg-harness")
JAVA_HOME = os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-21")
MAVEN_OPTS = ("--add-opens java.base/java.util=ALL-UNNAMED "
              "--add-opens java.base/java.lang=ALL-UNNAMED")

_cache: dict[str, dict] = {}


def sign_via_unidbg(api: str, ver: str, data: str, ts: str, devid: str,
                    utdid: str, ttid: str, uid: str = "", sid: str = "") -> dict:
    key = "|".join([api, ver, data, ts, devid, utdid, ttid, uid, sid])
    if key in _cache:
        return _cache[key]
    import tempfile
    fd, path = tempfile.mkstemp(suffix=".json", prefix="mtopdata_")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(data)
    args = [
        f"--api={api}", f"--v={ver}", f"--datafile={path}", f"--t={ts}",
        f"--devid={devid}", f"--utdid={utdid}", f"--ttid={ttid}",
    ]
    if uid:
        args.append(f"--uid={uid}")
    if sid:
        args.append(f"--sid={sid}")
    mvn = os.environ.get("MVN", "D:/apache-maven-3.9.6/bin/mvn.cmd")
    cmd = [mvn, "-s", "settings.xml", "-o", "-q", "exec:java",
           "-Dexec.mainClass=com.tmall.SignCli", "-Dsg.apkmap=true",
           "-Dexec.args=" + " ".join(args)]
    env = dict(os.environ, JAVA_HOME=JAVA_HOME, MAVEN_OPTS=MAVEN_OPTS)
    p = subprocess.run(cmd, cwd=HARNESS, env=env, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=300)
    out = (p.stdout or "") + (p.stderr or "")
    for line in out.splitlines():
        if line.startswith("__SIGN_JSON__"):
            d = json.loads(line[len("__SIGN_JSON__"):])
            _cache[key] = d
            return d
    raise RuntimeError("SignCli 没吐结果:\n" + out[-2000:])


def build_headers(sig: dict) -> dict:
    h = {
        "User-Agent": UA,
        "x-appkey": APP_KEY,
        "x-devid": DEVID,
        "x-ttid": TTID,
        "x-utdid": UTDID,
        "x-umt": UMT,
        "x-pv": "6.2",
        "x-bx-version": "6.5.90",
        "x-app-ver": "5.0.0",
        "x-platform": "android",
        "x-features": "27",
        "x-sign": sig.get("x-sign", ""),
        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        "Accept": "application/json",
    }
    if os.environ.get("SG_NOSIGN") == "1":
        h.pop("x-sign", None)
    sgext = os.environ.get("SG_SGEXT", "")
    if sgext:
        h["x-sgext"] = sgext
    if sig.get("x-mini-wua"):
        h["x-mini-wua"] = sig["x-mini-wua"]
    return h


def send(url: str, body: bytes | None, headers: dict, timeout: float = 15.0):
    req = urllib.request.Request(url, data=body, headers=headers,
                                 method="POST" if body is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return -1, f"NETWORK_ERROR::{e}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="mtop.taobao.mloginService.smsSend")
    ap.add_argument("--ver", default="1.0")
    ap.add_argument("--data", default=None)
    ap.add_argument("--datafile", default=None)
    ap.add_argument("--host", default="acs.m.taobao.com")
    a = ap.parse_args()

    if a.datafile:
        with open(a.datafile, encoding="utf-8") as f:
            data = f.read()
    else:
        data = a.data or "{}"
    ts = str(int(time.time() * 1000))
    sig = sign_via_unidbg(a.api, a.ver, data, ts, DEVID, UTDID, TTID)
    print(f"[+] x-sign = {sig.get('x-sign')}  (err={sig.get('err')})")
    print(f"[+] input  = {sig.get('input')}")

    headers = build_headers(sig)
    qs = urllib.parse.urlencode({
        "jsv": "2.6.1", "appKey": APP_KEY, "t": ts, "api": a.api, "v": a.ver,
        "type": "originaljson", "dataType": "json", "x-features": "27",
    })
    gw = f"https://{a.host}/gw/{a.api}/{a.ver}/"

    # 变体 A：params 全在 query，data 放 body
    st, txt = send(gw + "?" + qs, urllib.parse.urlencode({"data": data}).encode(), headers)
    print(f"[A] query+body  {st}  {txt[:400]}")

    # 变体 C：query 里补齐 app 常用参数 + x-sign 也放 query
    qs2 = dict(urllib.parse.parse_qsl(qs))
    qs2.update({"utdid": UTDID, "ttid": TTID, "x-features": "27",
                "x-appkey": APP_KEY, "x-sign": sig.get("x-sign", ""),
                "H5Request": "false", "data": data})
    q2 = urllib.parse.urlencode(qs2)
    st, txt = send(gw + "?" + q2, None, headers)
    print(f"[C] query-sink   {st}  {txt[:300]}")

    # 变体 D：GET 全 query，再带一个 h5 式 sign（空 token）
    import hashlib
    h5sign = hashlib.md5(f"&{ts}&{APP_KEY}&{data}".encode()).hexdigest()
    qs3 = dict(qs2)
    qs3["sign"] = h5sign
    st, txt = send(gw + "?" + urllib.parse.urlencode(qs3), None, headers)
    print(f"[D] h5sign too   {st}  {txt[:300]}")

    # 变体 B：GET 全 query
    st, txt = send(gw + "?" + qs + "&" + urllib.parse.urlencode({"data": data}), None, headers)
    print(f"[B] query-only  {st}  {txt[:400]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
