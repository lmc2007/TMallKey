/*
 * 只读抓包：把天猫校园发出的 mtop 明文请求（URL + 头）打到 frida 控制台。
 *
 * 为什么 hook SSL_write 而不是 Java 层：
 *   frida 17 移除了内建 Java 桥，而 App 的 mtop 走 OkHttp → Conscrypt → BoringSSL，
 *   明文的 HTTP 请求最终都会经过 libssl.so 的 SSL_write，这里拿到的就是完整报文。
 *
 * 目标：确认 ①真实 appKey ②请求到底带不带 x-sgext ③x-sign / x-mini-wua 的形态。
 */
'use strict';

var HITS = [];

function hookSslWrite() {
    var mods = Process.enumerateModules().filter(function (m) {
        return /^(libssl\.so|stable_cronet_libssl\.so)$/.test(m.name);
    });
    if (mods.length === 0) {
        send({ kind: 'warn', msg: '没有 libssl.so' });
        return;
    }
    mods.forEach(function (m) {
        var exp = Module.findGlobalExportByName
            ? Module.findGlobalExportByName('SSL_write')
            : Module.findExportByName(null, 'SSL_write');
        // 每个模块单独找自己的导出
        var addr = null;
        try {
            addr = Module.findExportByName(m.name, 'SSL_write');
        } catch (e) {
            addr = null;
        }
        if (!addr) {
            send({ kind: 'warn', msg: m.name + ' 找不到 SSL_write' });
            return;
        }
        send({ kind: 'info', msg: 'hook SSL_write @ ' + m.name + ' ' + addr });
        Interceptor.attach(addr, {
            onEnter: function (args) {
                try {
                    var buf = args[1];
                    var len = args[2].toInt32();
                    if (len <= 0 || len > 65536) return;
                    var s = buf.readUtf8String(Math.min(len, 8192));
                    if (!s) return;
                    // 只关心 mtop 报文
                    if (s.indexOf('mtop') < 0 && s.indexOf('acs.m.taobao') < 0 &&
                        s.indexOf('confong') < 0) return;
                    var first = s.split('\r\n')[0];
                    send({ kind: 'req', first: first, len: len, body: s.slice(0, 4096) });
                } catch (e) {
                    send({ kind: 'err', msg: String(e) });
                }
            }
        });
    });
}

hookSslWrite();
send({ kind: 'ready' });
