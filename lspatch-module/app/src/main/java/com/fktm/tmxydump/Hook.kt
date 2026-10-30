package com.fktm.tmxydump

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * LSPatch / Xposed 模块：把**天猫校园自己**的数据导出给 fkTM。
 *
 * 两类数据，全走本地回环 Socket（127.0.0.1:42741，fkTM 带 token 来拉）：
 *
 * 1. **登录 cookie**：模块在自己的进程里读 `CookieManager`，就是天猫校园自己的会话。
 * 2. **门锁凭证**：hook `VocLockHelperV2.unlock(Context, JSBParams, UnlockCallback)`，
 *    从 `JSBParams` 里直接拿 `macAddress` + `activationKey`。
 *    门锁是离线 BLE（activationKey 静态），不经过任何服务端校验，
 *    所以不受「新会话没绑校园档案」的影响。
 *
 * ## 为什么是 Socket
 *
 *  · 广播：fkTM 在后台被 ColorOS 冻结时收不到（实测）
 *  · Provider：Android 11+ 包可见性，天猫校园清单没声明 com.fktm.app → Unknown authority（实测）
 *  · 本地回环 Socket：不经过权限/可见性检查，token 防误连
 */
class Hook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PKG) return
        // 只挂主进程：多进程下 WebView 的 cookie 库会争锁
        if (lpparam.processName != TARGET_PKG) return

        XposedBridge.log("[tmxydump] loaded in ${lpparam.processName}")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.getOrNull(0) as? Context ?: return
                        startDumping(ctx.applicationContext ?: ctx)
                        startCookieServer()
                        hookDoorUnlock(lpparam.classLoader)
                    }
                },
            )
        } catch (t: Throwable) {
            XposedBridge.log("[tmxydump] hook attach failed: $t")
        }
    }

    // ---------------- 门锁凭证 ----------------

    /** 用户在补丁版天猫校园里每次点「开锁」，这里就多一条（按 mac 去重，保留最新）。 */
    private val doorLocks = ConcurrentHashMap<String, String>()

    /** MV 锁（动态 syncData）：抓到即证明该用户的锁是 MV 型而非 VOC。 */
    private val mvLocks = ConcurrentHashMap<String, String>()

    private fun hookDoorUnlock(classLoader: ClassLoader) {
        // V1 和 V2 的 unlock 签名完全一致：unlock(Context, JSBParams, UnlockCallback)。
        // 5.0.0 的 H5 桥固定走 V2，但旧锁固件 / 新版 App 可能走 V1 —— 两个都挂，按类存在与否容错。
        var hooked = 0
        for (cls in listOf(
            "com.tmall.campus.doorlock.VocLockHelperV2",
            "com.tmall.campus.doorlock.VocLockHelper",
            "com.tmall.campus.doorlock.session.VocLockManager",
        )) {
            try {
                XposedHelpers.findAndHookMethod(
                    cls,
                    classLoader,
                    "unlock",
                    "android.content.Context",
                    "com.tmall.campus.doorlock.JSBParams",
                    "com.tmall.campus.doorlock.UnlockCallback",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val jsb = param.args.getOrNull(1) ?: return
                            val mac = XposedHelpers.callMethod(jsb, "getMacAddress") as? String ?: return
                            val key = XposedHelpers.callMethod(jsb, "getActivationKey") as? String ?: return
                            if (mac.isBlank() || key.isBlank()) return
                            doorLocks[mac] = key
                            XposedBridge.log("[tmxydump] doorlock captured ($cls) mac=$mac keyLen=${key.length}")
                        }
                    },
                )
                hooked++
                XposedBridge.log("[tmxydump] $cls.unlock hooked")
            } catch (t: Throwable) {
                // 类不存在 = 该版本没有这个实现，正常；把情况记下来便于排障
                XposedBridge.log("[tmxydump] $cls not hooked: ${t.message}")
            }
        }
        if (hooked == 0) {
            XposedBridge.log(
                "[tmxydump] WARN: no VocLockHelper found — 天猫校园版本可能已变更，" +
                    "请把「我的-关于」里的版本号反馈给开发者",
            )
        }

        // MV 锁（ZMUnlockJsBridge → com.mvlock.supriseble，第二套门锁协议）。
        // syncData 是服务端签发的动态开锁帧，不能离线复用，但抓到它能确认「锁是 MV 型」，
        // 同时 fkTM 可把 mac+syncData 转发出去做一次在线开门验证。
        try {
            XposedHelpers.findAndHookMethod(
                "com.tmall.campus.bizwebview.plugin.ZMUnlockJsBridge",
                classLoader,
                "unlock",
                String::class.java,
                "android.taobao.windvane.jsbridge.WVCallBackContext",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val raw = param.args.getOrNull(0) as? String ?: return
                        XposedBridge.log("[tmxydump] ZM unlock params: $raw")
                        runCatching {
                            val obj = JSONObject(raw)
                            val mac = obj.optString("macAddress")
                            val sync = obj.optString("syncData")
                            if (mac.isNotBlank() && sync.isNotBlank()) {
                                mvLocks[mac] = sync
                            }
                        }
                    }
                },
            )
            XposedBridge.log("[tmxydump] ZMUnlockJsBridge.unlock hooked")
        } catch (t: Throwable) {
            XposedBridge.log("[tmxydump] ZMUnlockJsBridge not hooked: ${t.message}")
        }
    }

    // ---------------- cookie 导出 ----------------

    private fun startDumping(ctx: Context) {
        val handler = Handler(Looper.getMainLooper())
        var last = ""

        fun tick(fast: Int) {
            val json = dumpCookies() ?: run {
                handler.postDelayed({ tick(fast) }, if (fast > 0) INTERVAL_MS else SLOW_MS)
                return
            }
            if (json != last) {
                if (deliver(ctx, json)) {
                    last = json            // 只有真正送出去才记住，失败下轮重试
                }
            }
            // 前 5 分钟 20 秒一次，之后降为 60 秒一次，进程活着就一直导
            handler.postDelayed({ tick(maxOf(fast - 1, 0)) }, if (fast > 0) INTERVAL_MS else SLOW_MS)
        }

        handler.postDelayed({ tick(TICK_COUNT) }, START_DELAY_MS)
    }

    /** 读各个域下的 cookie，拼成 JSON；没有登录态就返回 null。 */
    private fun dumpCookies(): String? {
        val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return null
        val jar = dumpCookieMap(cm) ?: return null
        return JSONObject(jar).toString().also {
            XposedBridge.log("[tmxydump] ${jar.size} cookies")
        }
    }

    /** cookie map；没登录（无 unb/munb）返回 null。 */
    private fun dumpCookieMap(cm: CookieManager): Map<String, String>? {
        val jar = linkedMapOf<String, String>()
        for (u in HOSTS) {
            val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
            for (part in raw.split(';')) {
                val k = part.substringBefore('=', "").trim()
                if (k.isEmpty()) continue
                jar[k] = part.substringAfter('=', "").trim()
            }
        }
        // 没登录就别发（匿名也有 cookie2，容易误判）
        if (jar["unb"].isNullOrBlank() && jar["munb"].isNullOrBlank()) return null
        return jar
    }

    private fun deliver(ctx: Context, json: String): Boolean {
        // 1) Provider：目标进程即使被冻结也会被拉起（Android 11+ 跨包有可见性限制，可能失败）
        val viaProvider = runCatching {
            val bundle = android.os.Bundle().apply { putString(EXTRA_JSON, json) }
            val res = ctx.contentResolver.call(
                android.net.Uri.parse("content://$AUTHORITY"),
                "put", null, bundle,
            )
            res?.getBoolean("ok") == true
        }.getOrElse {
            XposedBridge.log("[tmxydump] provider failed: $it")
            false
        }

        // 2) 兜底广播（fkTM 在前台时能收到；后台被冻结则收不到）
        var viaBroadcast = false
        if (!viaProvider) {
            val intent = Intent(ACTION_COOKIES)
                .setPackage(RECEIVER_PKG)
                .putExtra(EXTRA_JSON, json)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            runCatching {
                ctx.sendBroadcast(intent)
                viaBroadcast = true
            }
        }

        Log.d(TAG, "cookies delivered via ${if (viaProvider) "provider" else "broadcast"}")
        XposedBridge.log(
            "[tmxydump] delivered via " +
                (if (viaProvider) "provider" else if (viaBroadcast) "broadcast" else "FAILED"),
        )
        return viaProvider || viaBroadcast
    }

    // ---------------- 本地 Socket 服务 ----------------

    private fun startCookieServer() {
        Thread {
            runCatching {
                // 显式 IPv4：getLoopbackAddress() 在部分设备返回 ::1，而 adb forward 走 127.0.0.1
                val server = ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
                XposedBridge.log("[tmxydump] cookie server on 127.0.0.1:$PORT")
                while (true) {
                    val sock = runCatching { server.accept() }.getOrNull() ?: continue
                    runCatching {
                        sock.soTimeout = 2000
                        val reader = sock.getInputStream().bufferedReader()
                        val line = reader.readLine()?.trim().orEmpty()
                        val out = sock.getOutputStream()
                        if (line != TOKEN) {
                            out.write("no token".toByteArray())
                        } else {
                            out.write(buildEnvelope().toByteArray())
                            XposedBridge.log("[tmxydump] served envelope to fkTM (doorlocks=${doorLocks.size})")
                        }
                        out.flush()
                    }
                    runCatching { sock.close() }
                }
            }.onFailure { XposedBridge.log("[tmxydump] server died: $it") }
        }.apply { isDaemon = true; name = "tmxy-cookie-server" }.start()
    }

    /** 组装 fkTM 要的数据包：cookies + doorlocks。没登录返回 "null"。 */
    private fun buildEnvelope(): String {
        val cm = runCatching { CookieManager.getInstance() }.getOrNull()
        val cookies = cm?.let { dumpCookieMap(it) }
        if (cookies == null) {
            return "null"
        }
        val obj = JSONObject()
        obj.put("cookies", JSONObject(cookies))
        val arr = JSONArray()
        doorLocks.forEach { (mac, key) ->
            arr.put(JSONObject().put("mac", mac).put("activationKey", key))
        }
        obj.put("doorlocks", arr)
        val mv = JSONArray()
        mvLocks.forEach { (mac, sync) ->
            mv.put(JSONObject().put("mac", mac).put("syncData", sync))
        }
        obj.put("mvlocks", mv)
        return obj.toString()
    }

    companion object {
        private const val TAG = "fkTM"
        private const val TARGET_PKG = "com.tmall.campus.and"
        private const val RECEIVER_PKG = "com.fktm.app"
        private const val AUTHORITY = "com.fktm.app.cookies"
        private const val ACTION_COOKIES = "com.fktm.app.ACTION_COOKIES"
        private const val EXTRA_JSON = "cookies_json"

        private const val START_DELAY_MS = 8_000L
        private const val INTERVAL_MS = 20_000L
        private const val TICK_COUNT = 15
        private const val SLOW_MS = 60_000L
        private const val PORT = 42741
        private const val TOKEN = "fktm-cookie-pull-v1"

        private val HOSTS = listOf(
            "https://share.confong.cn",
            "https://www.taobao.com",
            "https://h5api.m.taobao.com",
            "https://login.taobao.com",
        )
    }
}
