// 抓 anet 请求头：setHeaders(Map) / addHeader(String,String) / build()
var KEYS = ["cookie", "x-sign", "x-mini-wua", "x-sgext", "x-umt", "wua", "x-devid", "x-sid", "x-uid"];

function log(s) {
    console.log(s);
    try {
        var AT = Java.use("android.app.ActivityThread");
        var ctx = AT.currentApplication();
        var File = Java.use("java.io.File");
        var FOS = Java.use("java.io.FileOutputStream");
        var Str = Java.use("java.lang.String");
        var fos = FOS.$new(File.$new(ctx.getFilesDir(), "sg_hdr.log"), true);
        fos.write(Str.$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
}

function want(k) {
    var lk = String(k).toLowerCase();
    for (var i = 0; i < KEYS.length; i++) { if (lk.indexOf(KEYS[i]) >= 0) return true; }
    return false;
}

function dumpJavaMap(tag, map) {
    try {
        var m = Java.cast(map, Java.use("java.util.Map"));
        var ks = m.keySet().toArray();
        for (var i = 0; i < ks.length; i++) {
            var k = String(ks[i]);
            if (want(k)) log("[P] " + tag + " " + k + " = " + String(m.get(ks[i])));
        }
    } catch (e) { log("[P] " + tag + " dump 失败: " + e); }
}

Java.perform(function () {
    log("[P] start pid=" + Process.id);
    var B = Java.use("anet.channel.request.Request$Builder");

    B.setHeaders.overload('java.util.Map').implementation = function (m) {
        try { dumpJavaMap("setHeaders", m); } catch (e) { }
        return this.setHeaders(m);
    };
    log("[P] hooked setHeaders");

    B.addHeader.overload('java.lang.String', 'java.lang.String').implementation = function (k, v) {
        try { if (want(k)) log("[P] addHeader " + k + " = " + v); } catch (e) { }
        return this.addHeader(k, v);
    };
    log("[P] hooked addHeader");

    B.build.implementation = function () {
        var r = this.build();
        try {
            var m = r.getHeaders();
            dumpJavaMap("build", m);
        } catch (e) { }
        return r;
    };
    log("[P] hooked build");
});
