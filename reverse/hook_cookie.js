// 从 App 的 WebView CookieManager 取淘宝登录态（含 cookie2）
function log(s) {
    console.log(s);
    try {
        var AT = Java.use("android.app.ActivityThread");
        var ctx = AT.currentApplication();
        var File = Java.use("java.io.File");
        var FOS = Java.use("java.io.FileOutputStream");
        var Str = Java.use("java.lang.String");
        var fos = FOS.$new(File.$new(ctx.getFilesDir(), "sg_cookie.log"), true);
        fos.write(Str.$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
}

Java.perform(function () {
    log("[C] start");
    var urls = ["https://www.taobao.com", "https://h5.m.taobao.com", "https://acs.m.taobao.com"];
    try {
        var CM = Java.use("android.webkit.CookieManager");
        var cm = CM.getInstance();
        urls.forEach(function (u) {
            try {
                var c = cm.getCookie(u);
                log("[C] " + u + " -> " + (c === null ? "null" : c));
            } catch (e) {
                log("[C] " + u + " 异常: " + e);
            }
        });
    } catch (e) {
        log("[C] CookieManager 不可用: " + e);
    }
    log("[C] end");
});
