package com.fktm.app.credential

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.fktm.app.ILogcatGrab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume

/**
 * 借 Shizuku 抓取天猫校园下发的 activationKey。
 *
 * **为什么必须要 Shizuku：**
 * Android 4.1 起普通 App 只能读自己进程的日志（`READ_LOGS` 是系统级权限），
 * 而钥匙那行日志打在天猫校园**自己**的进程里，本 App 默认看不到。
 * Shizuku 提供免 root 的 adb 通道，让 [LogcatGrabService] 以 shell/root 身份
 * 运行 —— 等价于在电脑上跑 `adb logcat`，因此抓到的内容也一致。
 *
 * 走官方推荐的 **UserService** 而非已废弃的 `Shizuku.newProcess`
 * （后者在 13.1.5 里已被改成 private，随时可能彻底消失）。
 *
 * ## 结果以**文件**为准，而不是 binder 返回值
 *
 * 抓取全程用户都在天猫校园里，fkTM 在后台会被 ColorOS 冻结
 * （`do_freezer_trap`，实测连前台服务都挡不住）。冻结期间：
 *  · binder 回复送不回来 → `await` 不醒；
 *  · 冻结一旦超过上界，`withTimeoutOrNull` 会在解冻瞬间立刻超时 → 结果被当成失败丢掉。
 *
 * 所以服务端**命中即落盘**，本类把「文件」当唯一真相来源：
 *  · [consumeHit] 供界面在恢复前台时自取（轮询 / onStart）；
 *  · [capture] 在 binder 返回后、以及超时后，都会再捞一次文件兜底。
 *
 * 排障入口：`adb logcat -s fkTM:D`；服务端进度另见 `/data/local/tmp/fktm_grab_diag.txt`。
 */
object ShizukuLogcatGrabber {

    /** logcat 里统一用这个 tag 过滤我们自己打的诊断日志。 */
    const val TAG = "fkTM"

    /** 授权请求码（任意 int，只要回调里能对上）。 */
    const val REQUEST_CODE = 4207

    /**
     * 默认监听时长。
     *
     * 用户要「点开始抓取 → 走到门口 → 在天猫校园里开一次锁」，给足 4 分钟。
     * 命中会立刻结束，所以这个值只是上界，不是每次都要等满。
     */
    const val DEFAULT_TIMEOUT_MS = 240_000

    /** 结果文件的保鲜期：超过这个年纪的旧命中不再采用（避免翻出上一次的残留）。 */
    private const val HIT_MAX_AGE_MS = 15 * 60_000L

    /** 绑定阶段允许的额外耗时（总预算 = 抓取超时 + 这个值）。 */
    private const val BIND_SLACK_MS = 20_000L

    /** [versionToken] 读不到安装时间时的兜底令牌。 */
    private const val FALLBACK_VERSION = 1

    /**
     * UserService 进程的**版本令牌**。
     *
     * ⚠️ Shizuku 按 `(包名, processNameSuffix, version)` 缓存 UserService 进程。
     * 这个坑已经真实踩到过：覆盖安装新 APK 后，旧 APK 启动的 `com.fktm.app:grab`
     * 进程仍活着（而且会被 reparent 到 init，Shizuku 失去跟踪），
     * 若令牌不变，Shizuku 会把它当成有效缓存复用 —— 于是
     * **绑定的其实是旧代码的进程，`onServiceConnected` 再也不回调**，
     * 表现为「点了开始抓取就一直转圈」。
     *
     * 因此令牌取 **APK 的安装时间**（`lastUpdateTime`），每次安装天然不同，
     * 保证永远拿到与当前 APK 代码一致的新进程。
     */
    private fun versionToken(context: Context): Int = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrNull()
        ?.let { (it / 1000L).toInt() }
        ?: FALLBACK_VERSION

    /** 服务端命中后写入的结果文件（App 私有目录，只有本 App 与 root 能碰）。 */
    fun hitFile(context: Context): File = File(context.filesDir, "grab_hit.txt")

    /** Shizuku 服务是否在跑（没装 Shizuku / 没激活 / 已停止都返回 false）。 */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** 弹 Shizuku 的授权框；结果通过 [Shizuku.OnRequestPermissionResultListener] 异步回来。 */
    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            Log.w(TAG, "requestPermission failed", e)
        }
    }

    sealed interface Result {
        data class Found(val hit: LogcatParser.Hit) : Result
        data class Failed(val reason: String) : Result
        data object PermissionDenied : Result
    }

    private fun serviceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, LogcatGrabService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("grab")
            .debuggable(false)
            .version(versionToken(context))

    /**
     * 取走服务端写下的命中结果（取走即删除，保证只被消费一次）。
     *
     * 供界面在**恢复前台**时调用 —— 这正是「App 被冻结上百秒」的兜底：
     * 解冻那一刻把文件读走即可，不必等 binder 回复。
     */
    fun consumeHit(context: Context, maxAgeMs: Long = HIT_MAX_AGE_MS): LogcatParser.Hit? {
        val f = hitFile(context)
        if (!f.isFile) return null
        val text = runCatching { f.readText() }.getOrNull()
        val capturedAt = runCatching { f.lastModified() }.getOrDefault(0L)
        runCatching { f.delete() }
        return pickHit(text, capturedAt, System.currentTimeMillis(), maxAgeMs)
    }

    /**
     * 纯函数：从结果文件内容里判定命中（便于单测）。
     *
     * 文件内容就是 logcat 的**原始那一行**，因此直接复用 [LogcatParser]，
     * 不再引入第二套解析逻辑。
     */
    internal fun pickHit(
        text: String?,
        capturedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long,
    ): LogcatParser.Hit? {
        if (text.isNullOrBlank()) return null
        if (capturedAtMs > 0 && nowMs - capturedAtMs > maxAgeMs) return null
        return LogcatParser.parse(text)
    }

    /**
     * 绑定 UserService → 在 shell/root 身份下抓 logcat → 命中 activationKey 即返回。
     *
     * 全程有硬超时：**任何一步卡住都不会让界面无限转圈**。
     * 且**超时前会再捞一次结果文件** —— App 被冻结期间服务端往往已经抓到了。
     *
     * @param timeoutMs 最长监听（默认 [DEFAULT_TIMEOUT_MS]）
     */
    suspend fun capture(context: Context, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Result {
        Log.d(TAG, "capture: start, timeout=${timeoutMs}ms")
        if (!isAvailable()) {
            Log.w(TAG, "capture: Shizuku not running")
            return Result.Failed("Shizuku 未运行")
        }
        if (!hasPermission()) {
            Log.w(TAG, "capture: no permission")
            return Result.PermissionDenied
        }

        // 先清掉上一轮的残留，避免把旧命中当成新结果
        val resultPath = hitFile(context).absolutePath
        runCatching { File(resultPath).delete() }

        val args = serviceArgs(context)
        val apiRef = java.util.concurrent.atomic.AtomicReference<ILogcatGrab?>(null)

        /**
         * 工作线程最终拿到的结论。
         *
         * 必须有它：App 被冻结后一解冻，`withTimeoutOrNull` 会**立刻**判超时并取消协程，
         * 而那条阻塞着的 binder 线程可能同时刚拿到回复 —— 若此时 `cont` 已失效，
         * `resume` 会被丢掉，结果就白抓了。这里先落一份，超时出口再去取。
         */
        val settled = java.util.concurrent.atomic.AtomicReference<Result?>(null)

        /** 统一的「收尾」：先看结果文件，再退回解析 binder 返回值。 */
        fun collect(raw: String?, diag: String?): Result =
            consumeHit(context)?.let { Result.Found(it) }
                ?: raw?.let { LogcatParser.parse(it) }?.let { Result.Found(it) }
                ?: Result.Failed(
                    "监听 ${timeoutMs / 1000}s 未抓到钥匙。诊断：${diag ?: "无"}\n" +
                        "（若 lines=0，说明 logcat 没跑起来或没收到日志；" +
                        "若 lines>0 但 matched=0，说明过滤没匹配上）"
                )

        val outer = withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs + BIND_SLACK_MS) {
                suspendCancellableCoroutine { cont ->
                    var unbound = false
                    var connRef: ServiceConnection? = null

                    fun unbind() {
                        if (unbound) return
                        unbound = true
                        connRef?.let {
                            runCatching { Shizuku.unbindUserService(args, it, true) }
                                .onFailure { e -> Log.w(TAG, "unbind failed", e) }
                        }
                        Log.d(TAG, "unbind done")
                    }

                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            Log.d(TAG, "onServiceConnected: $name")
                            // 这个回调在主线程，而 grab() 会阻塞到命中或超时 —— 必须换线程
                            Thread {
                                val api = ILogcatGrab.Stub.asInterface(service)
                                apiRef.set(api)
                                val result = try {
                                    Log.d(TAG, "grab() begin, sink=$resultPath")
                                    val raw = api.grab(timeoutMs, resultPath)
                                    val diag = runCatching { api.diag() }.getOrNull()
                                    Log.d(TAG, "grab() done: rawLen=${raw.length}, diag=$diag")
                                    collect(raw, diag)
                                } catch (e: Throwable) {
                                    Log.w(TAG, "grab() threw", e)
                                    // 抛异常也可能只是回复途中出错，文件或许已写好
                                    consumeHit(context)?.let { Result.Found(it) }
                                        ?: Result.Failed("抓取失败：${e.message ?: e.javaClass.simpleName}")
                                } finally {
                                    unbind()
                                }
                                settled.set(result)
                                if (cont.isActive) cont.resume(result)
                            }.start()
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            Log.w(TAG, "onServiceDisconnected: $name")
                            unbound = true
                            if (cont.isActive) {
                                cont.resume(Result.Failed("UserService 已断开（Shizuku 可能被停止）"))
                            }
                        }
                    }
                    connRef = conn

                    try {
                        Log.d(TAG, "bindUserService() token=${versionToken(context)}")
                        Shizuku.bindUserService(args, conn)
                    } catch (e: Throwable) {
                        Log.w(TAG, "bindUserService failed", e)
                        unbind()
                        if (cont.isActive) {
                            cont.resume(Result.Failed("绑定 UserService 失败：${e.message}"))
                        }
                    }

                    cont.invokeOnCancellation { unbind() }
                }
            }
        }

        if (outer != null) return outer

        // 走到这里 = 整体超时 / 被取消。★ 两层兜底，缺一不可：
        //   ① 工作线程可能刚好已经拿到结论，只是来不及 resume（冻结解冻瞬间的典型情况）；
        //   ② 再不行就去看服务端落盘的结果文件 —— App 被冻住期间它往往已经抓到了。
        settled.get()?.let {
            Log.d(TAG, "capture: timed out, but worker had settled -> ${it.javaClass.simpleName}")
            return it
        }
        consumeHit(context)?.let {
            Log.d(TAG, "capture: timed out, but hit file was present -> success")
            return Result.Found(it)
        }

        val diag = runCatching { apiRef.get()?.diag() }.getOrNull()
        Log.w(TAG, "capture: overall timeout, diag=$diag")
        return Result.Failed(
            "整体超时（${(timeoutMs + BIND_SLACK_MS) / 1000}s）：UserService 未返回结果。" +
                (diag?.let { "\n服务端诊断：$it" } ?: "\n（连服务端诊断都没取到，说明绑定阶段就卡住了）")
        )
    }

}
