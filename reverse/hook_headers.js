// 抓真实请求头：anet 的 Request.Builder.addHeader
var KEYS = /cookie|x-sign|x-mini-wua|x-sgext|x-umt|^wua$|user-agent/i;

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

Java.perform(function () {
    log("[P] start pid=" + Process.id);
    var ok = 0;

    // anet 的 Request.Builder
    try {
        var B = Java.use("anet.channel.request.Request$Builder");
        B.addHeader.overload('java.lang.String', 'java.lang.String').implementation = function (k, v) {
            try {
                if (k && KEYS.test(k)) { log("[P] " + k + " = " + v); }
            } catch (e) { }
            return this.addHeader(k, v);
        };
        ok++;
        log("[P] hooked anet Request$Builder");
    } catch (e) {
        log("[P] anet 失败: " + e);
    }

    // 兼容：okhttp3
    try {
        var R = Java.use("okhttp3.Request$Builder");
        R.addHeader.overload('java.lang.String', 'java.lang.String').implementation = function (k, v) {
            try {
                if (k && KEYS.test(k)) { log("[P] okhttp " + k + " = " + v); }
            } catch (e) { }
            return this.addHeader(k, v);
        };
        ok++;
        log("[P] hooked okhttp3");
    } catch (e) {
        log("[P] okhttp 无: " + e);
    }

    log("[P] 完成，hook 数=" + ok);
});
