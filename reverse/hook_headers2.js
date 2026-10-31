// 抓真实请求头：hook anet Request.getHeaders() / makeHeaders()，只记关键头
var KEYS = ["cookie", "x-sign", "x-mini-wua", "x-sgext", "x-umt", "wua", "user-agent", "x-ttid"];

function log(s) {
    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var ctx = ActivityThread.currentApplication();
        var f = Java.use("java.io.File").$new(ctx.getFilesDir(), "sg_headers.log");
        var fos = Java.use("java.io.FileOutputStream").$new(f, true);
        fos.write(Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
    console.log(s);
}

function dumpMap(tag, map) {
    if (map === null) { log("[P] " + tag + " map=null"); return; }
    var jm;
    try { jm = Java.cast(map, Java.use("java.util.Map")); } catch (e) { log("[P] cast 失败 " + e); return; }
    var ks = jm.keySet().toArray();
    var out = [];
    for (var i = 0; i < ks.length; i++) {
        var k = String(ks[i]);
        var lk = k.toLowerCase();
        var hit = false;
        for (var j = 0; j < KEYS.length; j++) { if (lk === KEYS[j] || lk.indexOf(KEYS[j]) >= 0) { hit = true; break; } }
        if (!hit) continue;
        var v = String(jm.get(ks[i]));
        out.push(k + " = " + v);
    }
    if (out.length > 0) {
        log("[P] " + tag + " (" + out.length + " 个头)");
        out.forEach(function (s) { log("[P]   " + s); });
    }
}

Java.perform(function () {
    log("[P] start pid=" + Process.id);

    try {
        var R = Java.use("anet.channel.request.Request");
        R.getHeaders.implementation = function () {
            var m = this.getHeaders();
            try { dumpMap("getHeaders", m); } catch (e) { log("[P] dump 异常 " + e); }
            return m;
        };
        log("[P] hooked Request.getHeaders");
    } catch (e) { log("[P] getHeaders 失败: " + e); }

    try {
        var R2 = Java.use("anet.channel.request.Request");
        R2.makeHeaders.implementation = function () {
            var m = this.makeHeaders();
            try { dumpMap("makeHeaders", m); } catch (e) { log("[P] dump2 异常 " + e); }
            return m;
        };
        log("[P] hooked Request.makeHeaders");
    } catch (e) { log("[P] makeHeaders 失败: " + e); }
});
