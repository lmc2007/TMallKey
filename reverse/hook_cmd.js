// 命令普查：真机上所有 doCommandNative 调用（cmd 码 + 参数 + 返回值 + 首次调用栈）
// 目的：搞清真机 x-sign / x-sgext / x-mini-wua 到底由哪个 cmd 产出。
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";
var seen = {};

function log(s) {
    console.log(s);
    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var ctx = ActivityThread.currentApplication();
        var f = Java.use("java.io.File").$new(ctx.getFilesDir(), "sg_cmd.log");
        var fos = Java.use("java.io.FileOutputStream").$new(f, true);
        fos.write(Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
}

function brief(v) {
    if (v === null) return "null";
    if (typeof v === "string") return JSON.stringify(v.length > 120 ? v.substring(0, 120) + "..." : v);
    try { var s = String(v); return s.length > 80 ? s.substring(0, 80) + "..." : s; } catch (e) { return "<obj>"; }
}

function stackTop() {
    try {
        var st = Java.use("java.lang.Thread").currentThread().getStackTrace();
        var out = [];
        for (var i = 3; i < st.length && out.length < 6; i++) {
            var s = String(st[i]);
            if (s.indexOf("java.lang.Thread") >= 0 || s.indexOf("frida") >= 0) continue;
            out.push(s);
        }
        return out.join(" <- ");
    } catch (e) { return "?"; }
}

Java.perform(function () {
    log("[C] === start === " + new Date().toISOString());

    var target = null;
    Java.enumerateClassLoadersSync().forEach(function (l) {
        try { if (l.loadClass(CLS) !== null) { target = l; } } catch (e) { }
    });
    if (target === null) { log("[C] 找不到 JNICLibrary"); return; }
    Java.classFactory.loader = target;

    var C = Java.use(CLS);
    C.doCommandNative.overload('int', '[Ljava.lang.Object;').implementation = function (code, args) {
        var first = (seen[code] === undefined);
        seen[code] = (seen[code] || 0) + 1;
        var dump = "";
        try {
            var n = (args === null) ? 0 : args.length;
            for (var i = 0; i < n; i++) dump += " a" + i + "=" + brief(args[i]);
        } catch (e) { dump = " <err>"; }
        var r = this.doCommandNative(code, args);
        log("[C] " + code + dump + "  => " + brief(r) + (first ? "  [first] " + stackTop() : ""));
        return r;
    };
    log("[C] hook ok, 等待 mtop 流量…");
});
