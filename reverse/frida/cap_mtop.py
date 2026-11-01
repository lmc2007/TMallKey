#!/usr/bin/env python3
"""启动天猫校园并只读抓它的 mtop 明文请求（SSL_write）。

用法:
    python cap_mtop.py [--secs 40]

输出：每条命中写进 reverse/frida/mtop_capture.txt，同时在控制台摘要打印。
"""
from __future__ import annotations

import argparse
import os
import sys
import time

import frida

PKG = "com.tmall.campus.and"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "mtop_capture.txt")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--secs", type=int, default=40)
    a = ap.parse_args()

    dev = frida.get_usb_device()
    # 杀掉旧进程，spawn 才能在第一时间挂上 hook
    try:
        dev.kill(PKG)
    except Exception:
        pass
    time.sleep(1)

    pid = dev.spawn([PKG])
    print(f"[+] spawned {PKG} pid={pid}")
    session = dev.attach(pid)
    with open(os.path.join(HERE, "ssl_hook.js"), encoding="utf-8") as f:
        src = f.read()
    script = session.create_script(src)

    hits = []

    def on_msg(msg, data):
        p = msg.get("payload") or {}
        kind = p.get("kind")
        if kind == "req":
            hits.append(p)
            print(f"\n[REQ] {p['first'][:160]}")
            # 只打印关键头
            for line in p["body"].split("\r\n"):
                low = line.lower()
                if any(k in low for k in ("x-sgext", "x-sign", "x-mini-wua", "x-umt",
                                          "x-appkey", "cookie2", "x-utdid", "host")):
                    print("      " + line[:200])
        elif kind in ("info", "warn", "err", "ready"):
            print(f"[{kind}] {p.get('msg', '')}")

    script.on("message", on_msg)
    script.load()
    dev.resume(pid)
    print(f"[*] 跑 {a.secs}s，等它自然发请求…")

    try:
        for _ in range(a.secs):
            time.sleep(1)
    except KeyboardInterrupt:
        pass

    with open(OUT, "w", encoding="utf-8") as f:
        for h in hits:
            f.write("=== " + h["first"] + "\n" + h["body"] + "\n\n")
    print(f"\n[+] 共 {len(hits)} 条请求，写入 {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
