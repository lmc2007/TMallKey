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
                        appCtx = ctx.applicationContext ?: ctx
                        startDumping(appCtx ?: ctx)
                        startCookieServer()
                        hookDoorUnlock(lpparam.classLoader)
                        hookMtopParams(lpparam.classLoader)
                        hookMtopResult(lpparam.classLoader)
                    }
                },
            )
        } catch (t: Throwable) {
            XposedBridge.log("[tmxydump] hook attach failed: $t")
        }
    }

    // ---------------- mtop 请求探针（定位门锁密钥接口） ----------------

    /** api -> data 的最近一次请求；只留门锁相关的，方便一次性定位下发密钥的接口。 */
    private val mtopCalls = ConcurrentHashMap<String, String>()

    /** 天猫校园自己的 Context（模块跑在它进程里）。 */
    @Volatile
    private var appCtx: Context? = null

    /**
     * 探针日志落文件。
     *
     * 这台 ROM 把第三方 App 的 logcat 全屏蔽了（fkTM 与天猫校园的 Log 都看不到），
     * 所以只能写文件，再由 PC 侧 `su -c cat` 读。
     */
    private fun probeLog(line: String) {
        runCatching {
            val ctx = appCtx ?: return
            val f = java.io.File(ctx.filesDir, "tmxydump_probe.log")
            if (f.length() > 200_000) f.delete()
            f.appendText(line + java.lang.System.lineSeparator())
        }
    }

    /**
     * 挂 mtopsdk 的参数构建点。
     *
     * H5 的 mtop 调用最终都会走这里，所以能一次拿到 `api` 和 `data`（已序列化的业务参数），
     * 用来确认「门锁密钥是哪个接口下发的、参数长什么样」。
     */
    private fun hookMtopParams(classLoader: ClassLoader) {
        // H5 的 mtop 调用走 WindVane 的 MtopWVPlugin（不是原生 mtopsdk 那条链），
        // execute(action, params, callback) 的 params 就是 {api, data, ...} 的明文 JSON。
        val sigs = listOf(
            "com.taobao.mtop.wvplugin.MtopWVPlugin",
        )
        var hooked = 0
        for (cls in sigs) {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    cls,
                    classLoader,
                    "execute",
                    String::class.java,
                    String::class.java,
                    "android.taobao.windvane.jsbridge.WVCallBackContext",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val action = param.args.getOrNull(0) as? String ?: return
                            val raw = param.args.getOrNull(1) as? String ?: return
                            if (!raw.contains("door") && !raw.contains("lock")) return
                            probeLog("wv $action $raw")
                        }
                    },
                )
                hooked++
                probeLog("MtopWVPlugin hooked")
            }.onFailure {
                probeLog("MtopWVPlugin NOT hooked: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
        if (hooked == 0) probeLog("MtopWVPlugin: 0 hooks installed")
    }

    /**
     * 抓 mtop 的**响应体**。
     *
     * 门锁列表 `guide.door.lock.user.get` 的响应里有没有 `secretKey` 决定了
     * 「打开门锁页就能拿到钥匙」还是「必须点一次开锁」—— 目前只能靠实测确认。
     *
     * `MtopWVPlugin.wvCallback(MtopResult)` 是 H5 mtop 所有响应的统一落点，
     * `MtopResult.dataString` 就是 data 的 JSON 原文。
     */
    private fun hookMtopResult(classLoader: ClassLoader) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                "com.taobao.mtop.wvplugin.MtopWVPlugin",
                classLoader,
                "wvCallback",
                "com.taobao.mtop.wvplugin.MtopResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val r = param.args.getOrNull(0) ?: return
                        val data = runCatching {
                            XposedHelpers.getObjectField(r, "dataString") as? String
                        }.getOrNull() ?: return
                        val ok = runCatching {
                            XposedHelpers.getBooleanField(r, "success")
                        }.getOrDefault(false)
                        // 只留门锁相关的，别把整页响应都写进文件
                        if (!data.contains("secretKey") && !data.contains("macId") &&
                            !data.contains("deviceInfo") && !data.contains("lock")
                        ) return
                        probeLog("mtopResp ok=$ok len=${data.length} ${data.take(2000)}")
                    }
                },
            )
            probeLog("MtopResult hooked")
        }.onFailure {
            probeLog("MtopResult NOT hooked: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    // ---------------- 门锁凭证 ----------------

    /** 用户在补丁版天猫校园里每次点「开锁」，这里就多一条（按 mac 去重，保留最新）。 */
    private val doorLocks = ConcurrentHashMap<String, String>()

    /** MV 锁（动态 syncData）：抓到即证明该用户的锁是 MV 型而非 VOC。 */
    private val mvLocks = ConcurrentHashMap<String, String>()

    /** 上一次成功推给 fkTM 的整包内容（cookie 或门锁变了才重推）。 */
    @Volatile
    private var lastPushed: String = ""

    /** 抓到一个新门锁后，1.2 秒内合并推送一次，不用等下一个 tick。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var flushScheduled = false

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
                            scheduleFlush()
                            probeLog("doorlock $cls mac=$mac keyLen=${key.length}")
                            XposedBridge.log("[tmxydump] doorlock captured ($cls) mac=$mac keyLen=${key.length}")
                        }
                    },
                )
                hooked++
                probeLog("$cls.unlock hooked")
                XposedBridge.log("[tmxydump] $cls.unlock hooked")
            } catch (t: Throwable) {
                // 类不存在 = 该版本没有这个实现，正常；把情况记下来便于排障
                probeLog("$cls NOT hooked: ${t.javaClass.simpleName}: ${t.message}")
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
                            if (mac.isNotBlank()) {
                                // syncData 为空也记 MAC：fkTM 只需要知道「这把是 MV 锁」，
                                // 开锁帧它自己用会话现取（服务端一次一密，缓存没用）
                                mvLocks[mac] = sync
                                probeLog("mvlock mac=$mac syncLen=${sync.length}")
                                scheduleFlush()
                            }
                        }
                    }
                },
            )
            probeLog("ZMUnlockJsBridge.unlock hooked")
            XposedBridge.log("[tmxydump] ZMUnlockJsBridge.unlock hooked")
        } catch (t: Throwable) {
            XposedBridge.log("[tmxydump] ZMUnlockJsBridge not hooked: ${t.message}")
        }
    }

    // ---------------- cookie 导出 ----------------

    private fun startDumping(ctx: Context) {
        val handler = Handler(Looper.getMainLooper())

        var tickCount = 0

        fun tick(fast: Int) {
            // 整轮包 try/catch：以前这里抛一次异常整条定时链就断了，且没有任何痕迹
            try {
                tickCount++
                val json = buildPushPayload()
                if (json == null) {
                    if (tickCount <= 3 || tickCount % 10 == 0) {
                        probeLog("tick#$tickCount no cookies (unb/munb 为空或 CookieManager 未就绪)")
                    }
                } else if (json == lastPushed) {
                    if (tickCount <= 3) probeLog("tick#$tickCount 内容未变，跳过")
                } else if (deliver(ctx, json)) {
                    lastPushed = json      // 只有真正送出去才记住，失败下轮重试
                    probeLog("tick#$tickCount delivered len=${json.length}")
                } else {
                    probeLog("tick#$tickCount deliver FAILED len=${json.length}")
                }
            } catch (t: Throwable) {
                probeLog("tick error: ${t.javaClass.simpleName}: ${t.message}")
            }
            // 前 5 分钟 20 秒一次，之后降为 60 秒一次，进程活着就一直导
            handler.postDelayed({ tick(maxOf(fast - 1, 0)) }, if (fast > 0) INTERVAL_MS else SLOW_MS)
        }

        handler.postDelayed({ tick(TICK_COUNT) }, START_DELAY_MS)
    }

    /**
     * 推送用的整包：`{cookies, doorlocks, mvlocks}`。
     *
     * 以前 tick 只推 cookie，门锁只能在 fkTM 回来点开饮水页时通过 Socket 拉 ——
     * 用户「打开一次天猫校园」就再也拿不到门锁。改成整包后，
     * 抓到门锁的那一轮推送就会把钥匙一起带过去。
     */
    private fun buildPushPayload(): String? {
        val env = buildEnvelope()
        return if (env == "null") null else env
    }

    /**
     * 抓到新门锁 / MV 锁后立刻安排一次推送。
     *
     * 门锁是在「打开门锁页」那一刻才出现在内存里的，而用户通常紧接着就切走，
     * 等 20 秒的 tick 会错过。这里 1.2 秒后合并推一次。
     */
    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        mainHandler.postDelayed({
            flushScheduled = false
            val ctx = appCtx ?: return@postDelayed
            val json = buildPushPayload() ?: return@postDelayed
            if (json != lastPushed && deliver(ctx, json)) {
                lastPushed = json
                probeLog("flush delivered len=${json.length} doorlocks=${doorLocks.size} mv=${mvLocks.size}")
            }
        }, 1_200L)
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
        // cookie2 有多条（.taobao.com 与 .confong.cn），CM 身份只认 confong 那条，
        // 所以单独记下来最后写，避免被后面的 taobao 域覆盖。
        var confongCookie2: String? = null
        for (u in HOSTS) {
            val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
            val isConfong = u.contains("confong", ignoreCase = true)
            for (part in raw.split(';')) {
                val k = part.substringBefore('=', "").trim()
                if (k.isEmpty()) continue
                val v = part.substringAfter('=', "").trim()
                if (k == "cookie2" && isConfong) confongCookie2 = v
                jar[k] = v
            }
        }
        confongCookie2?.let { jar["cookie2"] = it }
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
