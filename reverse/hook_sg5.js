// 同步版：hook 之后立即在真机上调用 signRequest（不用 setTimeout）
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";
var APPKEY = "34270422";

function log(s) {
    console.log(s);
    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var ctx = ActivityThread.currentApplication();
        var f = Java.use("java.io.File").$new(ctx.getFilesDir(), "sg_hook.log");
        var fos = Java.use("java.io.FileOutputStream").$new(f, true);
        fos.write(Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
}

Java.perform(function () {
    log("[H] start");

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

        var rts = [7, 3, 2];
        for (var k = 0; k < rts.length; k++) {
            var rt = rts[k];
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
        }
    } catch (e) {
        log("[S] 自测失败: " + e);
    }
    log("[H] end");
});
