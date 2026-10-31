// 列出 anet Request$Builder 的所有方法，找正确的 header 注入点
Java.perform(function () {
    function dump(cls) {
        try {
            var C = Java.use(cls);
            var out = [];
            C.class.getDeclaredMethods().forEach(function (m) { out.push(m.toString()); });
            console.log("[M] " + cls + " (" + out.length + ")\n  " + out.join("\n  "));
        } catch (e) {
            console.log("[M] " + cls + " 不可用: " + e);
        }
    }
    dump("anet.channel.request.Request$Builder");
    dump("anet.channel.request.Request");
});
