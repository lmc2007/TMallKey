// 发现脚本：找出 SG 的类加载器与真实 JNI 类名
Java.perform(function () {
    var hits = [];
    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            if (name.indexOf("JNICLibrary") >= 0 || name.indexOf("SecurityGuard") >= 0
                || name.indexOf("wireless.security") >= 0) {
                hits.push(name);
            }
        },
        onComplete: function () {
            console.log("[D] 命中 " + hits.length + " 个类");
            hits.slice(0, 80).forEach(function (n) { console.log("[D] " + n); });
        }
    });

    // 列出 classloader
    try {
        var loaders = Java.enumerateClassLoadersSync();
        console.log("[D] loaders=" + loaders.length);
        loaders.forEach(function (l, i) {
            try { console.log("[D] loader" + i + " = " + l); } catch (e) { }
        });
    } catch (e) {
        console.log("[D] enumerateClassLoaders 失败: " + e);
    }

    // SG 的 so 是否已加载
    Process.enumerateModules().forEach(function (m) {
        if (/sgmain|sgmiddle|sgsecurity|libmain|libsecuritybody|sgcore/i.test(m.name)) {
            console.log("[D] so: " + m.name + " @ " + m.base);
        }
    });
});
