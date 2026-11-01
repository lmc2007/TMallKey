#!/usr/bin/env python3
"""常驻只读抓包：把天猫校园发的 mtop 明文请求实时写盘。

和 cap_mtop.py 的区别：
 · 不 spawn（不重启 App），直接 attach 当前进程 —— 用户点一次就能立刻抓到
 · 同时 hook **所有**带 SSL_write 的库（libssl / stable_cronet_libssl / libopenssl / libttboringssl），
   因为不知道 mtop 实际走哪条 TLS 栈
 · 命中即刻 flush 到 mtop_capture.txt，不用等结束

用法:
    python watch_mtop.py [--secs 180]
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time

import frida

PKG = "com.tmall.campus.and"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "mtop_capture.txt")

JS = r"""
var seen = {};
var hooked = [];

function tryHook(m) {
    if (seen[m.name]) return;
    var addr = null;
    try {
        m.enumerateExports().forEach(function (e) {
            if (e.name === 'SSL_write' || e.name === 'SSL_write_ex') addr = e.address;
        });
    } catch (e) { return; }
    if (!addr) return;
    seen[m.name] = 1;
    hooked.push(m.name);
    Interceptor.attach(addr, {
        onEnter: function (args) {
            try {
                var len = args[2].toInt32();
                if (len <= 0 || len > 65536) return;
                var s = args[1].readUtf8String(Math.min(len, 8192));
                if (!s) return;
                if (s.indexOf('mtop') < 0 && s.indexOf('acs.m.taobao') < 0 &&
                    s.indexOf('confong') < 0) return;
                send({mod: m.name, req: s.slice(0, 8192)});
            } catch (e) {}
        }
    });
}

// 先扫已经在内存里的，再盯住后续 dlopen 的 —— tnet 的 TLS 栈往往是"用时才加载"，
// 只在 attach 那一刻枚举会漏掉它。
Process.enumerateModules().forEach(tryHook);
Process.attachModuleObserver({
    onAdded: function (m) { tryHook(m); }
});
setTimeout(function () { send({hooked: hooked}); }, 2000);
"""


def pid_of(pkg: str) -> int | None:
    out = subprocess.run(["adb", "shell", f"pidof {pkg}"], capture_output=True, text=True).stdout.strip()
    return int(out.split()[0]) if out else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--secs", type=int, default=180)
    a = ap.parse_args()

    pid = pid_of(PKG)
    if not pid:
        print(f"[-] {PKG} 没在跑，先启动它")
        return 1
    print(f"[+] attach pid={pid}")

    dev = frida.get_usb_device()
    session = dev.attach(pid)
    script = session.create_script(JS)

    hits = []
    fh = open(OUT, "w", encoding="utf-8")

    def on_msg(msg, data):
        p = msg.get("payload") or {}
        if "hooked" in p:
            print(f"[+] 已 hook: {p['hooked']}")
            return
        if "req" in p:
            hits.append(p["req"])
            first = p["req"].split("\r\n")[0]
            print(f"\n[REQ {len(hits)}] {first[:170]}")
            for line in p["req"].split("\r\n"):
                low = line.lower()
                if any(k in low for k in ("x-sgext", "x-sign", "x-mini-wua", "x-umt",
                                          "x-appkey", "cookie2", "x-utdid", "host:")):
                    print("      " + line[:220])
            fh.write(f"=== {p['mod']} {first}\n{p['req']}\n\n")
            fh.flush()

    script.on("message", on_msg)
    script.load()
    print(f"[*] 等你操作天猫校园，最多 {a.secs}s …")

    try:
        for _ in range(a.secs):
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    fh.close()
    print(f"\n[+] 共 {len(hits)} 条，已写入 {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
