/*
 * Hook SecurityGuard 的 JNI 入口，打印 doCommand 的真实入参。
 *
 * 用法：
 *   frida -U -f com.tmall.campus.and -l reverse/hook_sg.js --no-pause
 *   frida -U -n <进程名> -l reverse/hook_sg.js
 *
 * 目的：拿到真机 10101 / 10401 的完整参数，和 unidbg 里的逐项对比，
 * 定位 x-sign 报 608 到底缺什么。
 */

function dumpArgs(code, args) {
    var parts = [];
    try {
        if (args !== null && args !== undefined) {
            var n = args.length;
            for (var i = 0; i < n; i++) {
                var a = args[i];
                var v;
                if (a === null) {
                    v = 'null';
                } else {
                    v = String(a);
                    if (v.length > 300) {
                        v = v.substring(0, 300) + '…(' + v.length + ')';
                    }
                }
                parts.push('[' + i + ']=' + v);
            }
        }
    } catch (e) {
        parts.push('(argerr ' + e + ')');
    }
    console.log('[SG] cmd=' + code + ' ' + parts.join(' | '));
}

function hookJNICLibrary() {
    var candidates = [
        'com.taobao.wireless.security.adapter.JNICLibrary',
        'com.alibaba.wireless.security.adapter.JNICLibrary'
    ];
    var hooked = 0;
    candidates.forEach(function (name) {
        try {
            var J = Java.use(name);
            J.doCommandNative.implementation = function (code, args) {
                dumpArgs(code, args);
                return this.doCommandNative(code, args);
            };
            hooked++;
            console.log('[hook] ' + name + ' ok');
        } catch (e) {
            console.log('[hook] ' + name + ' skip: ' + e);
        }
    });
    return hooked;
}

function hookSig() {
    // SecurityGuardManager.getSecureSignatureComp() 的 signRequest 也一起看，便于对照 INPUT
    try {
        var C = Java.use('com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent');
        console.log('[hook] ISecureSignatureComponent found (interface)');
    } catch (e) {
    }
}

Java.perform(function () {
    console.log('[hook] java ready');
    hookJNICLibrary();
    hookSig();
});

// 有些进程 classloader 较晚就绪，再补一次
setTimeout(function () {
    Java.perform(function () {
        hookJNICLibrary();
    });
}, 3000);
