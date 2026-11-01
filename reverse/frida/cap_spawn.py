#!/usr/bin/env python3
"""spawn 天猫校园 + 全链路抓 mtop 明文请求。

抓包点三路并行（不知道它走哪条）：
  1. libtnet.so 的 NAL_session_SendHead / NAL_session_SendData —— 阿里 tnet 的 HTTP/2 头发送
  2. 任何模块导出的 SSL_write / SSL_write_ex —— 普通 TLS 栈
  3. 模块监控：hook 后新 dlopen 的库也补挂一遍

用法: python cap_spawn.py [--secs 45]
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

JS = r"""
var hooked = [];
var seen = {};

function hookTnet() {
    var m = null;
    try { m = Process.getModuleByName('libtnet.so'); } catch (e) { return; }
    ['NAL_session_SendHead', 'NAL_session_SendData'].forEach(function (fn) {
        if (seen[fn]) return;
        var a = null;
        try { m.enumerateExports().forEach(function (e) { if (e.name === fn) a = e.address; }); } catch (e) { return; }
        if (!a) return;
        seen[fn] = 1;
        hooked.push(fn);
        Interceptor.attach(a, {
            onEnter: function (args) {
                for (var i = 0; i < 4; i++) {
                    try {
                        var s = args[i].readUtf8String(2048);
                        if (!s || s.length < 10) continue;
                        if (/mtop|: |POST |GET |HTTP/.test(s)) {
                            send({ src: fn, text: s });
                            return;
                        }
                    } catch (e) {}
                }
            }
        });
    });
}

function hookSsl(m) {
    if (seen['ssl:' + m.name]) return;
    var addr = null;
    try {
        m.enumerateExports().forEach(function (e) {
            if (e.name === 'SSL_write' || e.name === 'SSL_write_ex') addr = e.address;
        });
    } catch (e) { return; }
    if (!addr) return;
    seen['ssl:' + m.name] = 1;
    hooked.push('ssl:' + m.name);
    Interceptor.attach(addr, {
        onEnter: function (args) {
            try {
                var len = args[2].toInt32();
                if (len <= 0 || len > 65536) return;
                var s = args[1].readUtf8String(Math.min(len, 4096));
                if (s && (s.indexOf('mtop') >= 0 || s.indexOf('POST ') >= 0 || s.indexOf('GET ') >= 0))
                    send({ src: 'ssl:' + m.name, text: s });
            } catch (e) {}
        }
    });
}

function sweep() {
    Process.enumerateModules().forEach(hookSsl);
    hookTnet();
}

sweep();
Process.attachModuleObserver({ onAdded: function (m) { hookSsl(m); hookTnet(); } });
setInterval(sweep, 1500);
setTimeout(function () { send({ hooked: hooked }); }, 6000);
"""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--secs", type=int, default=45)
    a = ap.parse_args()

    dev = frida.get_usb_device()
    try:
        dev.kill(PKG)
    except Exception:
        pass
    time.sleep(1)

    pid = dev.spawn([PKG])
    session = dev.attach(pid)
    script = session.create_script(JS)
    hits = []
    fh = open(OUT, "w", encoding="utf-8")

    def on_msg(msg, data):
        p = msg.get("payload") or {}
        if "hooked" in p:
            print(f"[+] hook 列表: {p['hooked']}")
            return
        if "text" in p:
            hits.append(p)
            print(f"\n[{p['src']}] {p['text'][:400]}")
            fh.write(f"=== {p['src']}\n{p['text']}\n\n")
            fh.flush()

    script.on("message", on_msg)
    script.load()
    dev.resume(pid)
    print(f"[*] 已启动并注入，观察 {a.secs}s")

    for _ in range(a.secs):
        time.sleep(1)
    fh.close()
    print(f"\n[+] 共 {len(hits)} 条，写入 {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
