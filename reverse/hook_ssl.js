// 纯 native：hook libttboringssl 的 SSL_write / SSL_read 抓明文 HTTP
// （本机 frida Java 桥不可用，故全部走 Interceptor，不碰 Java）
var MOD = "libttboringssl.so";
var mod = Process.findModuleByName(MOD);
if (mod === null) { console.log("[S] no " + MOD); }
else {
    var pWrite = mod.findExportByName("SSL_write");
    var pRead = mod.findExportByName("SSL_read");
    console.log("[S] write=" + pWrite + " read=" + pRead);

    var out = null;
    try {
        // 写到 /data/local/tmp（su 已 root，SELinux 需要放宽；先试 files 目录）
        out = new File("/data/local/tmp/ssl_dump.log", "a");
    } catch (e) { console.log("[S] file open fail " + e); }

    function emit(s) {
        console.log(s);
        try { if (out) { out.write(s + "\n"); out.flush(); } } catch (e) { }
    }

    function dump(tag, ptr, len) {
        try {
            if (len <= 0 || len > 65536) return;
            var s = ptr.readUtf8String(len);
            if (s === null) return;
            var low = s.toLowerCase();
            if (low.indexOf("mtop") < 0 && low.indexOf("x-sign") < 0 && low.indexOf("x-mini-wua") < 0
                && low.indexOf("x-sgext") < 0 && low.indexOf("login") < 0) return;
            emit("=== " + tag + " len=" + len + " ===\n" + s.substring(0, 3000));
        } catch (e) { }
    }

    Interceptor.attach(pWrite, {
        onEnter: function (args) { dump("W", args[1], args[2].toInt32()); }
    });
    Interceptor.attach(pRead, {
        onEnter: function (args) { this.buf = args[1]; this.len = args[2].toInt32(); },
        onLeave: function (ret) {
            var n = ret.toInt32();
            if (n > 0) dump("R", this.buf, n);
        }
    });
    console.log("[S] hooks installed");
}
