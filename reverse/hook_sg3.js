// 在自定义 ClassLoader 里 hook SG 的 JNICLibrary.doCommandNative(int, Object[])
// 输出：真机每条命令的 code + 参数 + 返回值
var CLS = "com.taobao.wireless.security.adapter.JNICLibrary";

function log(s) { console.log(s); }

Java.perform(function () {
    var target = null;
    Java.enumerateClassLoadersSync().forEach(function (l) {
        try {
            if (l.loadClass(CLS) !== null) { target = l; }
        } catch (e) { }
    });

    if (target === null) {
        log("[H] 所有 loader 都加载不到 " + CLS);
        return;
    }
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
                else {
                    var s;
                    try { s = String(v); } catch (e) { s = "<obj>"; }
                    dump += " |a" + i + "=" + s;
                }
            }
        } catch (e) { dump = " <dump err: " + e + ">"; }
        log("[H] >> " + code + dump);

        var r = this.doCommandNative(code, args);

        var rs;
        try { rs = (r === null) ? "null" : (typeof r === "string" ? JSON.stringify(r) : String(r)); }
        catch (e) { rs = "?"; }
        log("[H] << " + code + " = " + rs);
        return r;
    };
    log("[H] hook 完成（doCommandNative）");
});
