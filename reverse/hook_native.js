// native 层：拦截 art::JNI::RegisterNatives，找到 doCommandNative 的真实函数指针并 hook
// 不受 Java ClassLoader 多拷贝影响
function log(s) {
    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var ctx = ActivityThread.currentApplication();
        var f = Java.use("java.io.File").$new(ctx.getFilesDir(), "sg_native.log");
        var fos = Java.use("java.io.FileOutputStream").$new(f, true);
        fos.write(Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8"));
        fos.flush();
        fos.close();
    } catch (e) { }
    console.log(s);
}

function hookRegisterNatives() {
    var syms = [
        "_ZN3art3JNI15RegisterNativesEP7_JNIEnvP7_jclassPK15JNINativeMethodi",
        "RegisterNatives"
    ];
    var addr = null;
    for (var i = 0; i < syms.length && addr === null; i++) {
        try { addr = Module.findExportByName("libart.so", syms[i]); } catch (e) { }
    }
    if (addr === null) { log("[N] 找不到 RegisterNatives"); return; }

    Interceptor.attach(addr, {
        onEnter: function (args) {
            try {
                var n = args[3].toInt32();
                if (n <= 0 || n > 200) return;
                for (var i = 0; i < n; i++) {
                    var base = args[2].add(i * 24);           // sizeof(JNINativeMethod)=24 (arm64)
                    var namePtr = base.readPointer();
                    var fnPtr = base.add(16).readPointer();
                    var name = namePtr.readCString();
                    if (name && (name === "doCommandNative" || name.indexOf("Command") >= 0)) {
                        log("[N] 找到 " + name + " @ " + fnPtr);
                        hookDoCommand(fnPtr, name);
                    }
                }
            } catch (e) { log("[N] onEnter 异常: " + e); }
        }
    });
    log("[N] RegisterNatives 已挂");
}

function hookDoCommand(fnPtr, name) {
    try {
        Interceptor.attach(fnPtr, {
            onEnter: function (args) {
                var code = args[2].toInt32();
                var arr = args[3];
                var desc = "ptr=" + arr;
                try {
                    var env = Java.vm.getEnv();
                    var len = env.getArrayLength(arr);
                    desc = "len=" + len;
                    for (var i = 0; i < len; i++) {
                        var o = env.getObjectArrayElement(arr, i);
                        if (o === null) { desc += " |a" + i + "=null"; continue; }
                        var s = null;
                        try { s = o.toString(); } catch (e) { }
                        if (s !== null && s.length > 0 && s.indexOf("java.lang.") < 0) {
                            desc += " |a" + i + "=" + JSON.stringify(s);
                        } else {
                            desc += " |a" + i + "=<" + (s === null ? "?" : s) + ">";
                        }
                    }
                } catch (e) {
                    desc += " <parse err: " + e + ">";
                }
                log("[N] >> " + code + " " + desc);
                this.code = code;
            },
            onLeave: function (retval) {
                log("[N] << " + this.code + " = " + retval);
            }
        });
        log("[N] hook " + name + " 完成");
    } catch (e) {
        log("[N] hook " + name + " 失败: " + e);
    }
}

hookRegisterNatives();
