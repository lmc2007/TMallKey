// hook + 真机自测；日志写到 App 私有目录（App 有写权限，root 可读）
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";
var APPKEY = "34270422";
var out = null;

function openLog() {
    if (out !== null) return;
    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var ctx = ActivityThread.currentApplication();
        var f = Java.use("java.io.File").$new(ctx.getFilesDir(), "sg_hook.log");
        out = Java.use("java.io.FileOutputStream").$new(f, true);
    } catch (e) {
        console.log("[H] openLog 失败: " + e);
    }
}

function log(s) {
    console.log(s);
    try {
        if (out !== null) {
            out.write(Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8"));
            out.flush();
        }
    } catch (e) { }
}

Java.perform(function () {
    openLog();
    var target = null;
    Java.enumerateClassLoadersSync().forEach(function (l) {
        try { if (l.loadClass(CLS) !== null) { target = l; } } catch (e) { }
    });
    if (target === null) { log("[H] 找不到 JNICLibrary"); return; }
    Java.classFactory.loader = target;

    var C = Java.use(CLS);
    C.doCommandNative.overload('int', '[Ljava.lang.Object;').implementation = function (code, args) {
        var dump = "";
        try {
            var n = (args === null) ? 0 : args.length;
            for (var i = 0; i < n; i++) {
                var v = args[i];
                if (v === null) { dump += " |a" + i + "=null"; }
                else if (typeof v === "string") { dump += " |a" + i + "=" + JSON.stringify(v); }
                else { var s; try { s = String(v); } catch (e) { s = "<obj>"; } dump += " |a" + i + "=" + s; }
            }
        } catch (e) { dump = " <err>"; }
        log("[H] >> " + code + dump);
        var r = this.doCommandNative(code, args);
        var rs;
        try { rs = (r === null) ? "null" : (typeof r === "string" ? JSON.stringify(r) : String(r)); } catch (e) { rs = "?"; }
        log("[H] << " + code + " = " + rs);
        return r;
    };
    log("[H] hook 完成");

    setTimeout(function () {
        try {
            var ActivityThread = Java.use("android.app.ActivityThread");
            var ctxApp = ActivityThread.currentApplication().getApplicationContext();
            var SGM = Java.use("com.alibaba.wireless.security.open.SecurityGuardManager");
            var mgr = SGM.getInstance(ctxApp);
            log("[S] mgr=" + mgr);

            var PC = Java.use("com.alibaba.wireless.security.open.SecurityGuardParamContext");
            var comp = mgr.getSecureSignatureComp();
            log("[S] comp=" + comp);

            var hm = Java.use("java.util.HashMap").$new();
            hm.put("INPUT", "test_input_for_sign");

            [7, 3, 2].forEach(function (rt) {
                try {
                    var pc = PC.$new();
                    pc.appKey.value = APPKEY;
                    pc.requestType.value = rt;
                    pc.paramMap.value = hm;
                    var sign = comp.signRequest(pc, "");
                    log("[S] 真机 type=" + rt + " -> " + sign);
                } catch (e) {
                    log("[S] 真机 type=" + rt + " 异常: " + e);
                }
            });
        } catch (e) {
            log("[S] 自测失败: " + e);
        }
    }, 1000);
});
