// Hook SG 的 Java→native 唯一入口：JNICLibrary.doCommandNative(int, Object[])
// 目的：拿真机 10101 / 10102 / 10401 / 20102 的完整入参与返回值
var LOG = "/data/local/tmp/sg_hook.log";

function log(s) {
    console.log(s);
    try {
        var f = new File(LOG, "a");
        f.write(s + "\n");
        f.flush();
        f.close();
    } catch (e) { }
}

log("[H] script loaded pid=" + Process.id);

Java.perform(function () {
    log("[H] java ready");

    var targets = [
        "com.taobao.wireless.security.adapter.JNICLibrary",
        "com.alibaba.wireless.security.jyh.auth.JNICLibrary",
        "com.alibaba.wireless.security.open.JNICLibrary"
    ];

    var hooked = 0;
    targets.forEach(function (cls) {
        try {
            var C = Java.use(cls);
            var names = [];
            C.class.getDeclaredMethods().forEach(function (m) { names.push(m.getName()); });
            log("[H] " + cls + " methods=" + names.join(","));

            C.doCommandNative.overload('[Ljava.lang.Object;').implementation = function (args) {
                var code = (args && args.length > 0) ? args[0] : null;
                var dump = "";
                try {
                    for (var i = 1; i < args.length; i++) {
                        var v = args[i];
                        dump += " |a" + i + "=" + (v === null ? "null" : (typeof v === "string" ? JSON.stringify(v) : String(v)));
                    }
                } catch (e) { dump = " <dump err>"; }
                log("[H] >> " + code + dump);
                var r = this.doCommandNative(args);
                var rs;
                try { rs = (r === null ? "null" : (typeof r === "string" ? JSON.stringify(r) : String(r))); } catch (e) { rs = "?"; }
                log("[H] << " + code + " = " + rs);
                return r;
            };
            hooked++;
        } catch (e) {
            log("[H] skip " + cls + " : " + e);
        }
    });

    log("[H] hooked count=" + hooked);
});
