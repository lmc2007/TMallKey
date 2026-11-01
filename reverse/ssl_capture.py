#!/usr/bin/env python3
"""纯 native 抓包：hook libttboringssl 的 SSL_write/SSL_read，把明文 mtop 请求打到 stdout。

用法:
    python ssl_capture.py <pid> [秒数] [过滤关键字]
因为本机 frida 的 Java 桥不可用，这里只用 Interceptor（不碰 Java）。
"""
import os
import sys
import time

import frida

SCRIPT = r"""
var LIBS = ["libttboringssl.so", "stable_cronet_libssl.so", "libssl.so", "libopenssl.so", "libssl.1.1.1j.so"];
var KEY = "__KEYS__";
var installed = 0;
    function hit(s) {
        var low = s.toLowerCase();
        var parts = KEY.split(",").filter(function (x) { return x.length > 0; });
        if (parts.length === 0) return true;
        for (var i = 0; i < parts.length; i++) if (low.indexOf(parts[i]) >= 0) return true;
        return false;
    }
    var seq = 0, MAXD = __MAXD__, hexLeft = __HEXN__;
    function dump(tag, ptr, len) {
        try {
            if (len <= 0 || len > 65536) return;
            if (seq >= MAXD) return;
            var s = ptr.readUtf8String(len);
            if (s === null) return;
            if (!hit(s)) return;
            seq++;
            send({t: "data", m: "=== #" + seq + " " + tag + " len=" + len + " ===\n" + s.substring(0, 3000)});
        } catch (e) { }
    }
    LIBS.forEach(function (nm) {
        var mod = Process.findModuleByName(nm);
        if (mod === null) { send({t: "log", m: "[S] no " + nm}); return; }
        var pW = null, pR = null;
        try { pW = mod.findExportByName("SSL_write"); pR = mod.findExportByName("SSL_read"); } catch (e) { }
        if (pW === null) {
            mod.enumerateExports().forEach(function (e) {
                if (e.name === "SSL_write") pW = e.address;
                if (e.name === "SSL_read") pR = e.address;
            });
        }
        if (pW === null) { send({t: "log", m: "[S] " + nm + " no SSL_write"}); return; }
        send({t: "log", m: "[S] hook " + nm + " w=" + pW + " r=" + pR});
        installed++;
        Interceptor.attach(pW, {
            onEnter: function (a) {
                var len = a[2].toInt32();
                if (seq < MAXD) dump("W", a[1], len);
                if (hexLeft > 0 && len > 0) {
                    hexLeft--;
                    var h = "";
                    try { h = hexdump(a[1], {length: Math.min(len, 96), ansi: false}); } catch (e) { }
                    send({t: "data", m: "HEX[" + nm + "] len=" + len + "\n" + h});
                }
            }
        });
        // SSL_write_ex(ssl, buf, len, size_t *written) —— tnet/cronet 常用这个
        var pEx = null;
        try { pEx = mod.findExportByName("SSL_write_ex"); } catch (e) { }
        if (pEx === null) {
            mod.enumerateExports().forEach(function (e) { if (e.name === "SSL_write_ex") pEx = e.address; });
        }
        if (pEx !== null) {
            Interceptor.attach(pEx, {
                onEnter: function (a) {
                    var len = a[2].toInt32();
                    if (seq < MAXD) dump("Wex", a[1], len);
                    if (hexLeft > 0 && len > 0) {
                        hexLeft--;
                        var h = "";
                        try { h = hexdump(a[1], {length: Math.min(len, 96), ansi: false}); } catch (e) { }
                        send({t: "data", m: "HEXEX[" + nm + "] len=" + len + "\n" + h});
                    }
                }
            });
            send({t: "log", m: "[S] hook_ex " + nm + " @" + pEx});
        }
        if (pR !== null) Interceptor.attach(pR, {
            onEnter: function (a) { this.buf = a[1]; this.len = a[2].toInt32(); },
            onLeave: function (r) { var n = r.toInt32(); if (n > 0) dump("R", this.buf, n); }
        });
    });
    send({t: "log", m: "[S] hooks installed=" + installed});
"""


def main():
    pid = int(sys.argv[1])
    dur = float(sys.argv[2]) if len(sys.argv) > 2 else 60.0
    keys = sys.argv[3] if len(sys.argv) > 3 else ""
    dev = frida.get_device_manager().add_remote_device("127.0.0.1:27042")
    sess = dev.attach(pid)

    def on_msg(msg, data):
        if msg.get("type") == "send":
            print(msg["payload"].get("m", ""), flush=True)
        else:
            print("[ERR]", msg, flush=True)

    sess.on("detached", lambda reason, crash=None: print("[DETACH]", reason, crash, flush=True))
    maxd = os.environ.get("CAP_MAX", "600")
    hexn = os.environ.get("CAP_HEX", "250")
    script = sess.create_script(SCRIPT.replace("__KEYS__", keys)
                               .replace("__MAXD__", maxd).replace("__HEXN__", hexn))
    script.on("message", on_msg)
    script.load()
    t0 = time.time()
    while time.time() - t0 < dur:
        time.sleep(0.5)
    try:
        sess.detach()
    except Exception:
        pass
    print("[S] done", flush=True)


if __name__ == "__main__":
    main()
