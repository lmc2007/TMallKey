// 纯监看：记录 App 自己发起的每条 doCommandNative（写到 App 私有目录）
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";

function log(s) {
    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var ctx = ActivityThread.currentApplication();
        var f = Java.use("java.io.File").$new(ctx.getFilesDir(), "sg_watch.log");
        var fos = Java.use("java.io.FileOutputStream").$new(f, true);
        fos.write(Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
    console.log(s);
}

Java.perform(function () {
    log("[W] start pid=" + Process.id);

    var target = null;
    Java.enumerateClassLoadersSync().forEach(function (l) {
        try { if (l.loadClass(CLS) !== null) { target = l; } } catch (e) { }
    });
    if (target === null) { log("[W] 找不到 JNICLibrary"); return; }
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
        log("[W] >> " + code + dump);
        var r = this.doCommandNative(code, args);
        var rs;
        try { rs = (r === null) ? "null" : (typeof r === "string" ? JSON.stringify(r) : String(r)); } catch (e) { rs = "?"; }
        log("[W] << " + code + " = " + rs);
        return r;
    };
    log("[W] hook 完成");
});
