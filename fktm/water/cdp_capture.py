#!/usr/bin/env python3
"""WebView CDP 取证工具 —— 校园饮水服务逆向用。

为什么需要它：天猫校园的 mtop 请求走 WindVane 原生通道，Network 域抓不到包；
唯一可行路径是注入 hook 包住 `lib.mtop.request`，把入参抄下来。

用法（手机 USB 或无线 adb 均可，脚本会自动挑设备）：

    python -m fktm.water.cdp_capture probe           # 列出 WebView targets
    python -m fktm.water.cdp_capture arm --reload    # 注入 hook（跨页面持久）
    python -m fktm.water.cdp_capture watch           # 实时打印捕获，落盘 jsonl
    python -m fktm.water.cdp_capture dump            # 一次性读手机端 localStorage
    python -m fktm.water.cdp_capture nav <url>       # 让当前 WebView 跳到指定 URL
    python -m fktm.water.cdp_capture clear           # 清空手机端累积

设计要点：
- hook 把每条记录写进 `localStorage.__fktm_cap`，所以**PC 掉线也不会丢数据**，
  回到电脑旁 `dump` 即可取回。这是无线链路抖动时的兜底。
- 同时用 `Page.addScriptToEvaluateOnNewDocument` 注册，保证**扫码跳转后的新页面**
  依然有 hook（前提：CDP socket 还连着）。
- 绝不打印 cookie / token；只记录 api 名与 data 入参。
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

LOCAL_PORT = 9222
PKG = "com.tmall.campus.and"
OUT_DIR = Path(__file__).resolve().parent / "captures"

ADB = os.environ.get("ADB", "adb")


# --------------------------------------------------------------------------- adb

def adb(*args: str, serial: str | None = None, check: bool = True) -> str:
    cmd = [ADB]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} 失败: {p.stderr.strip() or p.stdout.strip()}")
    return p.stdout


def pick_device(prefer: str | None = None) -> str:
    """挑一个在线的设备：优先 TCP（无线），其次 USB。"""
    if prefer:
        return prefer
    out = adb("devices")
    tcp, usb = [], []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 2 or parts[1] != "device":
            continue
        (tcp if ":" in parts[0] else usb).append(parts[0])
    if tcp:
        return tcp[0]
    if usb:
        return usb[0]
    raise RuntimeError("没有在线设备。先 adb connect <手机IP>:5555 或插 USB。")


def find_devtools_socket(serial: str) -> str:
    out = adb("shell", "cat /proc/net/unix", serial=serial)
    names = set()
    for line in out.splitlines():
        if "webview_devtools_remote" in line:
            for tok in line.split():
                # /proc/net/unix 里抽象 socket 带前导 '@'，adb forward 要不带
                tok = tok.lstrip("@")
                if tok.startswith("webview_devtools_remote"):
                    names.add(tok)
    if not names:
        raise RuntimeError(
            "没找到 webview_devtools_remote —— 天猫校园的 WebView 没起或调试口没开。"
            "先把 App 开到饮水页面再试。"
        )
    # 多个时取 pid 最大的（最新进程）
    def pid(n: str) -> int:
        tail = n.rsplit("_", 1)[-1]
        return int(tail) if tail.isdigit() else 0

    return sorted(names, key=pid)[-1]


def forward(serial: str, sock: str) -> None:
    adb("forward", f"tcp:{LOCAL_PORT}", f"localabstract:{sock}", serial=serial)


# --------------------------------------------------------------------------- cdp

class CDP:
    def __init__(self, ws_url: str) -> None:
        import websocket  # 延迟导入，probe 不必装

        # WebView devtools 只认 Host: localhost（含端口的 Host 会被丢弃），
        # target 里给的 ws url 是 ws://localhost/...，这里改成走本地转发端口。
        ws_url = ws_url.replace("ws://localhost/", f"ws://127.0.0.1:{LOCAL_PORT}/")
        ws_url = ws_url.replace("ws://127.0.0.1/", f"ws://127.0.0.1:{LOCAL_PORT}/")
        self.ws = websocket.create_connection(
            ws_url, timeout=15, max_size=None, host="localhost",
            suppress_origin=True,
        )
        self._id = 0
        self.events: list[dict] = []

    def call(self, method: str, params: dict | None = None, timeout: float = 15.0):
        import websocket

        self._id += 1
        mid = self._id
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        deadline = time.time() + timeout
        while time.time() < deadline:
            self.ws.settimeout(max(1.0, deadline - time.time()))
            try:
                raw = self.ws.recv()
            except websocket.WebSocketTimeoutException:
                break
            if not raw:
                continue
            msg = json.loads(raw)
            if msg.get("id") == mid:
                if "error" in msg:
                    raise RuntimeError(f"{method} 报错: {msg['error']}")
                return msg.get("result", {})
            if "method" in msg:
                self.events.append(msg)
        raise TimeoutError(f"{method} 超时")

    def eval(self, expr: str, timeout: float = 15.0):
        r = self.call(
            "Runtime.evaluate",
            {"expression": expr, "awaitPromise": True, "returnByValue": True},
            timeout=timeout,
        )
        if r.get("exceptionDetails"):
            raise RuntimeError(f"JS 异常: {r['exceptionDetails'].get('text')}")
        return r.get("result", {}).get("value")

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass


def list_targets(timeout: float = 6.0) -> list[dict]:
    """WebView 的 /json 端点有两个坑：
    1) 只认 `Host: localhost`，带端口的 127.0.0.1:9222 会被静默丢弃（连接建立但不回包）；
    2) 不发 Content-Length 也不主动关连接，用 urllib 会一直挂着。
    所以直接开 socket，自己拼 Host 并读到底。"""
    import socket as _sock

    s = _sock.create_connection(("127.0.0.1", LOCAL_PORT), timeout=timeout)
    s.sendall(b"GET /json/list HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
    s.settimeout(timeout)
    buf = b""
    while True:
        try:
            chunk = s.recv(65536)
        except _sock.timeout:
            break
        if not chunk:
            break
        buf += chunk
    s.close()
    text = buf.decode("utf-8", "replace")
    body = text.split("\r\n\r\n", 1)[1] if "\r\n\r\n" in text else text
    body = body.strip()
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        i, j = body.find("["), body.rfind("]")
        if i >= 0 and j > i:
            return json.loads(body[i : j + 1])
        raise RuntimeError(f"/json 返回无法解析: {body[:200]!r}")


def pick_page(targets: list[dict]) -> dict:
    pages = [t for t in targets if t.get("type") == "page"]
    if not pages:
        raise RuntimeError("没有 page target")
    # 优先 confong（饮水 H5）相关的页面
    for t in pages:
        if "confong" in (t.get("url") or ""):
            return t
    return pages[0]


# --------------------------------------------------------------------------- hook

HOOK_JS = r"""
(function () {
  var KEY = '__fktm_cap';
  try {
    window.__fktm_cap = JSON.parse(localStorage.getItem(KEY) || '[]');
  } catch (e) { window.__fktm_cap = []; }

  function push(rec) {
    try {
      rec.t = Date.now();
      rec.url = location.href;
      window.__fktm_cap.push(rec);
      if (window.__fktm_cap.length > 400) {
        window.__fktm_cap.splice(0, window.__fktm_cap.length - 400);
      }
      localStorage.setItem(KEY, JSON.stringify(window.__fktm_cap));
    } catch (e) {}
  }
  window.__fktm_push = push;
  window.__fktm_dump = function () {
    try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch (e) { return []; }
  };
  window.__fktm_clear = function () {
    try { localStorage.removeItem(KEY); } catch (e) {}
    window.__fktm_cap = [];
    return 'cleared';
  };

  function plain(v) {
    try {
      return JSON.parse(JSON.stringify(v, function (k, x) {
        if (typeof x === 'function') return undefined;
        return x;
      }));
    } catch (e) { return String(v); }
  }

  if (window.__fktm_hooked) { return 'already'; }
  window.__fktm_hooked = 1;

  var ticks = 0;
  var timer = setInterval(function () {
    ticks++;
    var m = window.lib && window.lib.mtop;
    if (!m) {
      if (ticks > 2400) { clearInterval(timer); }   // 120s 后放弃
      return;
    }
    if (m.__fktm_wrapped) { clearInterval(timer); return; }
    var orig = m.request;
    if (typeof orig !== 'function') { return; }

    m.request = function () {
      var args = Array.prototype.slice.call(arguments);
      var a0 = args[0] || {};
      var api = a0.api || '';
      try {
        push({
          kind: 'req',
          api: api,
          ver: a0.version || '',
          type: a0.type || '',
          data: a0.data || null,
          raw: plain(a0),
          stack: (new Error().stack || '').split('\n').slice(1, 5).join(' | ')
        });
      } catch (e) {}

      // 返回值也要抄：resNo / isvOrderId / shareServiceInfoId 都只在响应里
      var last = args[args.length - 1];
      if (typeof last === 'function') {
        args[args.length - 1] = function (res) {
          try { push({ kind: 'resp', api: api, resp: plain(res) }); } catch (e) {}
          return last.apply(this, arguments);
        };
        return orig.apply(this, args);
      }
      var ret = orig.apply(this, args);
      try {
        if (ret && typeof ret.then === 'function') {
          ret.then(
            function (res) { try { push({ kind: 'resp', api: api, resp: plain(res) }); } catch (e) {} },
            function (err) { try { push({ kind: 'resp', api: api, error: plain(err) }); } catch (e) {} }
          );
        }
      } catch (e) {}
      return ret;
    };
    m.__fktm_wrapped = 1;
    clearInterval(timer);
    push({ kind: 'hook', note: 'wrapped lib.mtop.request' });
  }, 50);

  return 'arming';
})()
"""


def do_probe(serial: str) -> int:
    sock = find_devtools_socket(serial)
    print(f"devtools socket : {sock}")
    forward(serial, sock)
    targets = list_targets()
    print(f"targets         : {len(targets)}")
    for t in targets:
        print(f"  [{t.get('type')}] {t.get('title','')[:40]!r}")
        print(f"       {t.get('url','')[:110]}")
        print(f"       ws={t.get('webSocketDebuggerUrl','')}")
    return 0


def do_arm(serial: str, reload_page: bool) -> int:
    sock = find_devtools_socket(serial)
    forward(serial, sock)
    page = pick_page(list_targets())
    print(f"target: {page.get('title','')[:40]!r}  {page.get('url','')[:90]}")
    cdp = CDP(page["webSocketDebuggerUrl"])
    try:
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        # 跨页面持久：后续每个新 document 自动注入
        cdp.call("Page.addScriptToEvaluateOnNewDocument", {"source": HOOK_JS})
        # 当前文档不会因为上一行而执行，必须再就地求值一次
        try:
            now = cdp.eval(HOOK_JS, timeout=10)
            print(f"当前文档注入: {now}")
        except Exception as e:
            print(f"当前文档注入失败（可能页面正忙）: {e.__class__.__name__}: {e}")
        if reload_page:
            # 直接 Page.navigate 会 chrome-error，用 JS 自跳
            cdp.eval("location.reload(); 1", timeout=6)
            time.sleep(0.5)
        print("已注册跨页面注入。等待页面就绪…")
        deadline = time.time() + 25
        state = None
        while time.time() < deadline:
            try:
                state = cdp.eval("window.__fktm_hooked ? 'hooked' : (window.lib ? 'lib-ready' : 'waiting')")
            except Exception as e:
                state = f"reconecting: {e.__class__.__name__}"
            if state == "hooked":
                break
            time.sleep(0.8)
        print(f"状态: {state}")
        if state == "hooked":
            print("✅ hook 已生效，进入 watch 模式或直接去操作手机")
        else:
            print("⚠ 未确认 hook 生效；如页面已跳转，重新跑一次 arm")
    finally:
        cdp.close()
    return 0


def do_nav(serial: str, url: str) -> int:
    sock = find_devtools_socket(serial)
    forward(serial, sock)
    page = pick_page(list_targets())
    cdp = CDP(page["webSocketDebuggerUrl"])
    try:
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Page.addScriptToEvaluateOnNewDocument", {"source": HOOK_JS})
        cdp.eval(f"location.href = {json.dumps(url)}; 'navigating'", timeout=6)
        print(f"已导航到 {url}（hook 会在新页面自动注入）")
    finally:
        cdp.close()
    return 0


def do_watch(serial: str, interval: float) -> int:
    sock = find_devtools_socket(serial)
    forward(serial, sock)
    page = pick_page(list_targets())
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"cap-{time.strftime('%Y%m%d-%H%M%S')}.jsonl"
    print(f"target: {page.get('url','')[:90]}")
    print(f"落盘  : {out}")
    print("Ctrl-C 结束\n")
    cdp = CDP(page["webSocketDebuggerUrl"])
    seen = 0
    fh = out.open("a", encoding="utf-8")
    try:
        cdp.call("Runtime.enable")
        while True:
            try:
                rows = cdp.eval("JSON.stringify(window.__fktm_dump ? window.__fktm_dump() : [])")
                rows = json.loads(rows) if rows else []
            except Exception as e:
                print(f"[{time.strftime('%H:%M:%S')}] 读不到（页面可能跳转中）: {e.__class__.__name__}")
                time.sleep(interval)
                continue
            if len(rows) > seen:
                for rec in rows[seen:]:
                    kind = rec.get("kind")
                    if kind == "req":
                        api = rec.get("api", "?")
                        print(f"\n▸ REQ  {api}  v={rec.get('ver','')}")
                        print(f"  data = {json.dumps(rec.get('data'), ensure_ascii=False)}")
                        if rec.get("raw"):
                            raw = {k: v for k, v in rec["raw"].items() if k not in ("data",)}
                            print(f"  其它 = {json.dumps(raw, ensure_ascii=False)[:300]}")
                    elif kind == "resp":
                        body = rec.get("resp")
                        if body is None:
                            body = {"__error__": rec.get("error")}
                        print(f"  ↳ RESP {rec.get('api','?')}: {json.dumps(body, ensure_ascii=False)[:700]}")
                    else:
                        print(f"\n▸ [{kind}] {rec.get('note','')}")
                    fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
                fh.flush()
                seen = len(rows)
            time.sleep(interval)
    except KeyboardInterrupt:
        print(f"\n结束，共 {seen} 条 → {out}")
    finally:
        fh.close()
        cdp.close()
    return 0


def do_dump(serial: str) -> int:
    """从手机 localStorage 取回累积记录（CDP 掉线后的兜底）。"""
    sock = find_devtools_socket(serial)
    forward(serial, sock)
    page = pick_page(list_targets())
    cdp = CDP(page["webSocketDebuggerUrl"])
    try:
        cdp.call("Runtime.enable")
        rows = json.loads(cdp.eval("JSON.stringify(window.__fktm_dump ? window.__fktm_dump() : [])") or "[]")
    finally:
        cdp.close()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / f"dump-{time.strftime('%Y%m%d-%H%M%S')}.jsonl"
    with out.open("w", encoding="utf-8") as fh:
        for r in rows:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"取回 {len(rows)} 条 → {out}\n")
    for r in rows:
        if r.get("kind") == "req":
            print(f"▸ {r.get('api')}  data={json.dumps(r.get('data'), ensure_ascii=False)[:400]}")
        elif r.get("kind") == "resp":
            print(f"  ↳ {r.get('api')}  resp={json.dumps(r.get('resp'), ensure_ascii=False)[:400]}")
    return 0


def do_clear(serial: str) -> int:
    sock = find_devtools_socket(serial)
    forward(serial, sock)
    page = pick_page(list_targets())
    cdp = CDP(page["webSocketDebuggerUrl"])
    try:
        cdp.call("Runtime.enable")
        print(cdp.eval("window.__fktm_clear ? window.__fktm_clear() : 'no-hook'"))
    finally:
        cdp.close()
    return 0


# --------------------------------------------------------------------------- main

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="WebView CDP 取证（饮水服务）")
    ap.add_argument("-s", "--serial", help="adb 设备序列号；默认自动选（优先无线）")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("probe", help="列出 WebView targets")
    p_arm = sub.add_parser("arm", help="注入 hook")
    p_arm.add_argument("--reload", action="store_true", help="注入后 reload 当前页")
    p_nav = sub.add_parser("nav", help="导航当前 WebView 到 URL")
    p_nav.add_argument("url")
    p_watch = sub.add_parser("watch", help="实时打印捕获")
    p_watch.add_argument("--interval", type=float, default=1.0)
    sub.add_parser("dump", help="从 localStorage 取回记录")
    sub.add_parser("clear", help="清空累积")

    args = ap.parse_args(argv)
    serial = pick_device(args.serial)
    print(f"device: {serial}\n")

    if args.cmd == "probe":
        return do_probe(serial)
    if args.cmd == "arm":
        return do_arm(serial, args.reload)
    if args.cmd == "nav":
        return do_nav(serial, args.url)
    if args.cmd == "watch":
        return do_watch(serial, args.interval)
    if args.cmd == "dump":
        return do_dump(serial)
    if args.cmd == "clear":
        return do_clear(serial)
    return 1


if __name__ == "__main__":
    sys.exit(main())
