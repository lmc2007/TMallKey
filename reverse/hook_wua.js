// 真机取 x-mini-wua，与 unidbg 的 69 字符对比
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";
var APPKEY = "34270422";

function log(s) {
    console.log(s);
    try {
        var AT = Java.use("android.app.ActivityThread");
        var ctx = AT.currentApplication();
        var File = Java.use("java.io.File");
        var FOS = Java.use("java.io.FileOutputStream");
        var Str = Java.use("java.lang.String");
        var f = File.$new(ctx.getFilesDir(), "sg_wua.log");
        var fos = FOS.$new(f, true);
        fos.write(Str.$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
}

Java.perform(function () {
    log("[W] start pid=" + Process.id);

    var target = null;
    Java.enumerateClassLoadersSync().forEach(function (l) {
        try { if (l.loadClass(CLS) !== null) { target = l; } } catch (e) { }
    });
    if (target === null) { log("[W] 找不到 JNICLibrary"); return; }
    Java.classFactory.loader = target;

    try {
        var AT = Java.use("android.app.ActivityThread");
        var ctx = AT.currentApplication().getApplicationContext();
        var SGM = Java.use("com.alibaba.wireless.security.open.SecurityGuardManager");
        var mgr = SGM.getInstance(ctx);

        var IBody = Java.use("com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent");
        var body = Java.cast(mgr.getInterface(IBody.class), IBody);
        log("[W] body=" + body);

        var ts = String(Date.now());
        var w1 = body.getSecurityBodyDataEx(ts, APPKEY, "", null, 8, 0);
        log("[W] 真机 wua(ts+appkey) len=" + (w1 === null ? "null" : w1.length) + " = " + w1);

        var w2 = body.getSecurityBodyDataEx(null, null, null, null, 8, 0);
        log("[W] 真机 wua(null,null,null) len=" + (w2 === null ? "null" : w2.length) + " = " + w2);
    } catch (e) {
        log("[W] 异常: " + e);
    }
    log("[W] end");
});
