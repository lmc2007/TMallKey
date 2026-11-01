#!/usr/bin/env python3
"""用无头 Chrome 复现校园 H5 的登录流程，判定 `.confong.cn` 的 cookie2 能否纯网页签发。

做法：
  1. 起一个干净的 Chrome（独立 user-data-dir，不带任何既有 cookie）；
  2. 通过 CDP 只注入 `.taobao.com` 域的 taobao 会话（不含 confong 的任何 cookie）；
  3. 打开校园/洗衣 H5 页面，抓全部网络请求与 Set-Cookie；
  4. 结束后读 getAllCookies，看 `.confong.cn` 上有没有 cookie2。

结论口径：
  - 出现了 `.confong.cn` 的 cookie2 → **纯网页可签发**，免 root 方案不需要 SecurityGuard；
  - 一直没有 → 说明必须走原生（native mtop / SG 签名）才能拿到 CM 身份。

用法:
    python reverse/web_sso_probe.py --cookies-db .tmp/ck/Cookies.db
    python reverse/web_sso_probe.py --url "https://share.confong.cn/app/tmall-xiaoyuan/tmxy-m-share/laundry"
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
import subprocess
import sys
import time
import urllib.request

import websocket  # websocket-client

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
TMP = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".tmp")
PROFILE = os.path.join(TMP, "chrome-sso")
PORT = 9333


class CDP:
    def __init__(self, ws_url: str):
        self.ws = websocket.create_connection(ws_url, timeout=30)
        self.mid = 0
        self.events: list[dict] = []

    def send(self, method: str, **params) -> dict:
        self.mid += 1
        mid = self.mid
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == mid:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg.get("result", {})
            if "method" in msg:
                self.events.append(msg)

    def pump(self, seconds: float) -> None:
        end = time.time() + seconds
        self.ws.settimeout(1.0)
        while time.time() < end:
            try:
                msg = json.loads(self.ws.recv())
            except Exception:  # noqa: BLE001
                continue
            if "method" in msg:
                self.events.append(msg)


def wait_devtools(timeout: float = 20.0) -> str:
    end = time.time() + timeout
    while time.time() < end:
        try:
            data = json.loads(urllib.request.urlopen(
                f"http://127.0.0.1:{PORT}/json/version", timeout=2).read())
            return data["webSocketDebuggerUrl"]
        except Exception:  # noqa: BLE001
            time.sleep(0.5)
    raise RuntimeError("Chrome 调试端口没起来")


def load_taobao_cookies(db: str) -> dict:
    c = sqlite3.connect(db)
    jar = {}
    for host, name, value in c.execute(
            "select host_key,name,value from cookies "
            "where (host_key like '%taobao%' or host_key like '%tmall%')"):
        jar.setdefault(name, value)
    # 明确剔除 confong 域，保证实验只注入 taobao 侧会话
    return jar


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cookies-db", default=os.path.join(TMP, "ck", "Cookies.db"))
    ap.add_argument("--url", default="https://share.confong.cn/app/tmall-xiaoyuan/"
                                    "tmxy-m-share/laundry?hideNavigator=true&statusBarMode=dark")
    ap.add_argument("--seconds", type=float, default=30.0)
    ap.add_argument("--keep-open", action="store_true")
    ap.add_argument("--jar", help="额外注入的 cookie（k=v; k2=v2，按 .confong.cn 域注入）")
    ap.add_argument("--bodies", action="store_true", help="抓 confong 接口的响应体")
    a = ap.parse_args()

    os.makedirs(PROFILE, exist_ok=True)
    proc = subprocess.Popen([
        CHROME, "--headless=new", f"--remote-debugging-port={PORT}",
        f"--user-data-dir={PROFILE}", "--no-first-run", "--no-default-browser-check",
        "--disable-gpu", "--disable-features=Translate", "about:blank",
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        ws_url = wait_devtools()
        print("[chrome] 已启动", ws_url)
        # 用浏览器级 ws 开 target，避免复用带缓存的页面
        browser = CDP(ws_url)
        tgt = browser.send("Target.createTarget", url="about:blank")
        tid = tgt["targetId"]
        att = browser.send("Target.attachToTarget", targetId=tid, flatten=True)
        sid = att["sessionId"]
        browser.ws.close()

        # 重连到页面级 ws
        pages = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=5).read())
        page_ws = next(p["webSocketDebuggerUrl"] for p in pages if p["id"] == tid)
        cdp = CDP(page_ws)
        cdp.send("Page.enable")
        cdp.send("Network.enable")

        jar = load_taobao_cookies(a.cookies_db)
        print(f"[cookie] 注入 taobao 域 {len(jar)} 条（不含 confong）")
        for k, v in jar.items():
            cdp.send("Network.setCookie", name=k, value=v, domain=".taobao.com",
                     path="/", secure=True, httpOnly=False)

        if a.jar:
            extra = {}
            for part in open(a.jar, encoding="utf-8").read().replace(chr(10), ";").split(";"):
                if "=" in part:
                    k, v = part.split("=", 1)
                    extra[k.strip()] = v.strip()
            print(f"[cookie] 额外注入 confong 域 {len(extra)} 条")
            for k, v in extra.items():
                cdp.send("Network.setCookie", name=k, value=v, domain=".confong.cn",
                         path="/", secure=True, httpOnly=True)
        print(f"[nav] {a.url}")
        cdp.send("Page.navigate", url=a.url)
        cdp.pump(a.seconds)

        # 分析事件
        reqs = {}
        setc = []
        for e in cdp.events:
            m = e.get("method")
            p = e.get("params", {})
            if m == "Network.requestWillBeSent":
                req = p["request"]
                reqs[p["requestId"]] = (req["method"], req["url"], req.get("postData") or "",
                                        req.get("headers") or {})
            elif m == "Network.responseReceivedExtraInfo":
                sc = p.get("headers", {}).get("set-cookie") or p.get("headers", {}).get("Set-Cookie")
                if sc:
                    setc.append((p.get("headers", {}).get(":authority") or "", sc))
        print(f"\n[req] 共 {len(reqs)} 条请求（confong / acs-m 全量列出）：")
        for rid, item in reqs.items():
            m, u = item[0], item[1]
            if "confong" not in u:
                continue
            if len(u) > 400:
                u = u[:400] + "…"
            print(f"   {m:4s} {u}")
            if len(item) > 2 and item[2]:
                print(f"        body={item[2][:500]}")
            hd = item[3] if len(item) > 3 else {}
            ck = hd.get("Cookie") or hd.get("cookie") or ""
            if ck:
                print(f"        cookie={ck[:400]}")
        print(f"\n[set-cookie] {len(setc)} 条")
        for host, sc in setc[:10]:
            print(f"   {host}: {sc[:160]}")

        if a.bodies:
            for rid, item in reqs.items():
                url = item[1]
                if "confong" not in url or "/h5/" not in url:
                    continue
                try:
                    body = cdp.send("Network.getResponseBody", requestId=rid)
                    txt = body.get("body", "")
                    import base64 as _b64
                    if body.get("base64Encoded"):
                        txt = _b64.b64decode(txt).decode("utf-8", "replace")
                except Exception as e:  # noqa: BLE001
                    continue
                print(chr(10) + "[BODY] " + url.split("/h5/")[-1].split("/")[0])
                print("       " + txt[:500].replace(chr(10), " "))

        cookies = cdp.send("Network.getAllCookies")["cookies"]
        cf = [c for c in cookies if "confong" in c["domain"]]
        tb2 = [c for c in cookies if "taobao" in c["domain"] and c["name"] == "cookie2"]
        print(f"\n[cookie] confong 域共 {len(cf)} 条：")
        for c in cf:
            print(f"   {c['domain']:24s} {c['name']:12s} len={len(c['value']):4d}")
        print(f"[cookie] taobao cookie2 存在: {bool(tb2)}")
        cm = [c for c in cf if c["name"] == "cookie2"]
        print("\n=== 判定 ===")
        if cm:
            print("confong cookie2 **已签发** ⇒ 纯网页可拿到 CM 身份（无需 SecurityGuard）")
            print("值指纹:", cm[0]["value"][:6] + "…", "len", len(cm[0]["value"]))
        else:
            print("confong cookie2 **未签发** ⇒ 网页流程拿不到，需原生/SG 参与")

        if a.keep_open:
            print("[chrome] 保持打开，Ctrl+C 结束")
            time.sleep(600)
        return 0
    finally:
        proc.terminate()


if __name__ == "__main__":
    sys.exit(main())
