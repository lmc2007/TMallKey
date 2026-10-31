// 真机取 umid（x-umt），看长度是否与 wua 的 96 字节差吻合
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";

function log(s) {
    console.log(s);
    try {
        var AT = Java.use("android.app.ActivityThread");
        var ctx = AT.currentApplication();
        var File = Java.use("java.io.File");
        var FOS = Java.use("java.io.FileOutputStream");
        var Str = Java.use("java.lang.String");
        var fos = FOS.$new(File.$new(ctx.getFilesDir(), "sg_umid.log"), true);
        fos.write(Str.$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
}

Java.perform(function () {
    log("[U] start");
    var target = null;
    Java.enumerateClassLoadersSync().forEach(function (l) {
        try { if (l.loadClass(CLS) !== null) { target = l; } } catch (e) { }
    });
    if (target === null) { log("[U] 找不到 JNICLibrary"); return; }
    Java.classFactory.loader = target;

    try {
        var AT = Java.use("android.app.ActivityThread");
        var ctx = AT.currentApplication().getApplicationContext();
        var SGM = Java.use("com.alibaba.wireless.security.open.SecurityGuardManager");
        var mgr = SGM.getInstance(ctx);

        var IUMID = Java.use("com.alibaba.wireless.security.open.umid.IUMIDComponent");
        var umid = Java.cast(mgr.getInterface(IUMID.class), IUMID);
        log("[U] umid comp=" + umid);
        try {
            var t0 = umid.getSecurityToken();
            log("[U] getSecurityToken() len=" + (t0 === null ? "null" : t0.length) + " = " + t0);
        } catch (e) { log("[U] getSecurityToken 异常: " + e); }
        try {
            var t1 = umid.getSecurityToken(0);
            log("[U] getSecurityToken(0) len=" + (t1 === null ? "null" : t1.length) + " = " + t1);
        } catch (e) { log("[U] getSecurityToken(0) 异常: " + e); }
    } catch (e) {
        log("[U] 异常: " + e);
    }
    log("[U] end");
});
