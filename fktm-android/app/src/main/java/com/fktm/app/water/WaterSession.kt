package com.fktm.app.water

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 登录态（cookie 集合）的唯一入口。
 *
 * ## 为什么最终是「一次性导入」
 *
 * 试过四条自研登录路径，全部被 app 身份挡住（详见 DEVDOC / PLAN-drinking）：
 *  · 淘宝 H5 登录   → 会话不是校园授权出来的，回 `GET_CAMPUS_USER_ERROR`
 *  · 淘宝 App 授权  → `AliMemberSDK.init(ctx, "tmall_campus")` + appkey/签名绑定
 *  · 手机号 + 短信  → 滑块能过，但 `riskControlInfo.wua` 是 `libsgsecuritybody` native 签名
 *  · 运营商一键登录 → 要天猫校园的运营商 appid，且同样要 wua
 *
 * 规律：**凡是要「产生会话」的路径，都要天猫校园的 appkey/appid + SecurityGuard 签名**，
 * 那是 app 的身份，不是用户凭据，外部伪造不出来。
 *
 * 所以这里换一个思路：**不去产生会话，直接把已有的会话搬过来**。
 * 天猫校园的 WebView cookie 库是**明文 SQLite**（只有 Chrome 浏览器才走 os_crypt 加密），
 * root 读一次即可；读完就落进 [WaterCookies] 的 Keystore 加密存储，
 * 之后**不再需要 root / Shizuku**，直到 cookie 过期（实测约 24 天）。
 */
object WaterSession {

    private const val TAG = "fkTM"

    /** 天猫校园的包名（注意不是 `com.tmall.campus`）。 */
    private const val TMALL_PKG = "com.tmall.campus.and"

    /** WebView 的 cookie 库有两代路径，从新到旧试。 */
    private val COOKIE_PATHS = listOf(
        "/data/data/$TMALL_PKG/app_webview/Default/Cookies",
        "/data/user/0/$TMALL_PKG/app_webview/Default/Cookies",
        "/data/data/$TMALL_PKG/app_webview/Cookies",
        "/data/user/0/$TMALL_PKG/app_webview/Cookies",
    )

    /** 只取这些域的 cookie（其余是别的业务的，没必要碰）。 */
    private val DOMAINS = listOf("confong.cn", "taobao.com", "tmall.com", "tmallcampus.com")

    @Volatile
    private var cache: MutableMap<String, String>? = null

    /** 最近一次「外部导入」的时间戳，UI 用它判断要不要刷新。 */
    @Volatile
    var importedAt: Long = 0L
        private set

    fun cached(): Map<String, String>? = cache

    /** 登录完成后由 UI 调用：写内存 + 加密落盘。 */
    fun adopt(context: Context, jar: Map<String, String>) {
        val map = LinkedHashMap(jar)
        cache = map
        WaterCookies.save(context, map)
        importedAt = System.currentTimeMillis()
    }

    fun clear(context: Context) {
        cache = null
        WaterCookies.clear(context)
    }

    /**
     * 取登录态：内存 → 加密存储。
     *
     * 没有就抛 [NotLoggedIn]，由 UI 引导去「导入」—— 不做静默兜底。
     */
    fun ensure(context: Context): Map<String, String> {
        cache?.let { if (it.isNotEmpty()) return it }

        val stored = WaterCookies.load(context)
        if (stored != null && stored.isNotEmpty()) {
            cache = LinkedHashMap(stored)
            Log.d(TAG, "water session: loaded ${stored.size} cookies from store")
            return cache!!
        }
        throw NotLoggedIn()
    }

    /**
     * 从本机天猫校园的 WebView 库里读一次 cookie（需要 root）。
     *
     * 全程只读：`su -c cat` 把库文件吐到 stdout，落到临时文件后用 SQLite 只读打开，
     * 读完立刻删临时文件。**不修改天猫校园的任何数据。**
     *
     * @throws IllegalStateException 读不到 / 解析失败（带人话原因）
     */
    suspend fun importFromDevice(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        var lastError = "未知原因"

        for (path in COOKIE_PATHS) {
            val bytes = readRaw(context, path)
            if (bytes == null || bytes.isEmpty()) {
                lastError = "读不到 $path（root 授权被拒？）"
                continue
            }
            val jar = runCatching { parseCookieDb(bytes, context) }.getOrElse { e ->
                lastError = "解析失败：${e.message}"
                null
            }
            if (!jar.isNullOrEmpty()) {
                Log.d(TAG, "water: imported ${jar.size} cookies from device")
                return@withContext jar
            }
            if (jar != null && jar.isEmpty()) lastError = "库是空的（天猫校园可能没登录过）"
        }

        throw IllegalStateException(
            "读不到天猫校园的登录态：$lastError\n" +
                "需要：① 手机已 root（KernelSU / Magisk）；② 允许本应用申请 root；" +
                "③ 天猫校园里处于已登录状态。"
        )
    }

    /**
     * 用 su 读文件。普通 app 的 su 跑在自己的 **mount namespace** 里，
     * `/data/data/<别的应用>` 可能根本不可见（实测：adb 的 su 看得到，App 的 su 报 ENOENT）。
     *
     * 所以这里把「路径写法 × su 调用形式」全试一遍：
     *  · 路径：`/data/data/...` 与 `/data/user/0/...`
     *  · 形式：`su -c` / `su -M -c`（--mount-master，进全局命名空间）
     * 每次失败都把 exit / stderr 原文写进诊断，便于判断是「没权限」还是「看不见」。
     */
    private fun readRaw(context: Context, path: String): ByteArray? {
        for (su in SU_BINARIES) {
            if (!File(su).exists() && su.startsWith("/")) continue
            for (args in SU_ARG_FORMS) {
                val argv = listOf(su) + args + listOf("cat $path")
                val bytes = runSu(context, argv)
                if (bytes != null && bytes.isNotEmpty()) return bytes
            }
        }
        return null
    }

    /** 执行一条 `su <args> <cmd>`，成功返回 stdout。 */
    private fun runSu(context: Context, argv: List<String>): ByteArray? = runCatching {
        val proc = ProcessBuilder(argv)
            .redirectErrorStream(false)
            .start()

        val out = ByteArrayOutputStream()
        val errBuf = StringBuilder()
        val reader = Thread {
            runCatching { proc.inputStream.use { it.copyTo(out) } }
        }.apply { isDaemon = true; start() }
        val errReader = Thread {
            runCatching {
                proc.errorStream?.bufferedReader()?.forEachLine { line ->
                    synchronized(errBuf) { errBuf.append(line).append(' ') }
                }
            }
        }.apply { isDaemon = true; start() }

        val finished = proc.waitFor(SU_TIMEOUT_S, TimeUnit.SECONDS)
        reader.join(3_000)
        errReader.join(1_000)
        if (!finished) {
            runCatching { proc.destroy() }
            WaterDiag.log(context, "su", "${argv.joinToString(" ")} 超时 ${SU_TIMEOUT_S}s")
            return@runCatching null
        }
        val code = proc.exitValue()
        if (code != 0 || out.size() == 0) {
            WaterDiag.log(
                context, "su",
                "${argv.joinToString(" ")} exit=$code out=${out.size()}B " +
                    "err=${errBuf.toString().trim().take(140)}",
            )
            null
        } else {
            WaterDiag.log(context, "su", "OK ${out.size()}B <- ${argv.joinToString(" ")}")
            out.toByteArray()
        }
    }.getOrElse { e ->
        WaterDiag.log(context, "su", "${argv.firstOrNull()} 启动失败 ${e.javaClass.simpleName}")
        null
    }

    /** cookie 库 → (name → value)，按域过滤，顺便丢掉已过期的。 */
    private fun parseCookieDb(bytes: ByteArray, context: Context): Map<String, String> {
        val tmp = File(context.cacheDir, "imported_ck.db")
        try {
            tmp.writeBytes(bytes)
            val jar = linkedMapOf<String, String>()
            val db = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
            db.use { d ->
                d.rawQuery("select host_key,name,value,expires_utc from cookies", null).use { c ->
                    val nowMs = System.currentTimeMillis()
                    while (c.moveToNext()) {
                        val host = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val value = c.getString(2) ?: ""
                        if (value.isEmpty()) continue
                        if (DOMAINS.none { host.removePrefix(".").endsWith(it) }) continue

                        // Chrome 的 expires_utc：1601-01-01 起的微秒数，0 = 会话 cookie
                        val expUtc = if (c.isNull(3)) 0L else c.getLong(3)
                        if (expUtc > 0) {
                            val expMs = expUtc / 1000 - CHROME_EPOCH_OFFSET_MS
                            if (expMs in 1 until nowMs - 60_000) continue
                        }
                        jar[name] = value
                    }
                }
            }
            return jar
        } finally {
            runCatching { tmp.delete() }
        }
    }

    /** 登录态不存在 / 已被清掉。 */
    class NotLoggedIn : Exception("还没有导入天猫校园的登录态")

    /**
     * CM 层探测：这批 cookie 在校园会员网关眼里算不算「已登录」。
     *
     * 重新登录天猫校园后，新会话在 CM 层可能是「未登录」（服务端校园档案没绑定），
     * device.info 能过但 water.start 会回 `GET_CAMPUS_USER_ERROR`。
     * 所以自动同步前必须探测，探测不过就**不要覆盖**已验证可用的会话。
     */
    fun campusProbeOk(jar: Map<String, String>): Boolean = runCatching {
        val c = MtopClient(LinkedHashMap(jar))
        c.call("mtop.tmall.campus.member.app.user.get", emptyMap())
        true
    }.getOrElse { false }

    /** 登录态属于哪个账号：`munb` / `unb` 就是用户 ID。 */
    fun accountId(jar: Map<String, String>): String =
        jar["munb"]?.takeIf { it.isNotBlank() }
            ?: jar["unb"]?.takeIf { it.isNotBlank() }
            ?: ""

    /** 展示用：保留头尾，中间打码。 */
    fun maskAccount(id: String): String = when {
        id.length <= 6 -> id
        else -> id.take(5) + "***" + id.takeLast(3)
    }

    /** 1601-01-01 → 1970-01-01 的毫秒差。 */
    private const val CHROME_EPOCH_OFFSET_MS = 11_644_473_600_000L

    /** su 可能的位置（KernelSU / Magisk / 原生）。 */
    private val SU_BINARIES = listOf(
        "su", "/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su",
    )

    /** su 的调用形式：`-M` 进全局 mount namespace，普通 app 才看得到别的应用数据。 */
    private val SU_ARG_FORMS = listOf(
        listOf("-M", "-c"),
        listOf("--mount-master", "-c"),
        listOf("-c"),
    )

    /** 首次授权会弹框，留给用户点「允许」的时间要够。 */
    private const val SU_TIMEOUT_S = 45L
}
