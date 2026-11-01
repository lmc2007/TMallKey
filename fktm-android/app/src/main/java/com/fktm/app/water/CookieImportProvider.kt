package com.fktm.app.water

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 给 LSPatch 模块用的「推Cookie」入口。
 *
 * ## 为什么不用广播
 *
 * 模块跑在天猫校园进程里，fkTM 在后台时会被 ColorOS 冻结（`OplusHansManager`），
 * **冻结中的 App 收不到广播**（实测：前台能收、后台静默丢弃）。
 * 而 ContentProvider 调用会**主动拉起目标进程**，冻结拦不住。
 *
 * ## 协议
 *
 * ```
 * authority : com.fktm.app.cookies
 * call("put", null, Bundle{ cookies_json = "{...}" })
 * ```
 *
 * exported=true 意味着其它应用也能伪造调用，但伪造的 cookie 只会让他自己用不了，
 * 不会泄露任何东西。
 */
class CookieImportProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_PUT) return null
        val raw = extras?.getString(EXTRA_JSON) ?: return null

        val env = ModuleEnvelope.parse(raw)
        val jar = env?.cookies.orEmpty()
        val account = WaterSession.accountId(jar)
        val ctx = requireNotNull(context)
        if (account.isBlank()) {
            WaterDiag.log(ctx.applicationContext, "providerImport", "no unb/munb, ignored")
            return Bundle().apply { putBoolean("ok", false) }
        }

        // CM 层探测不过就不覆盖（新会话可能没绑校园档案，water.start 会失败）
        if (!probeBlocking(jar)) {
            WaterDiag.log(ctx.applicationContext, "providerImport", "CM 层未绑定，忽略")
            return Bundle().apply { putBoolean("ok", false) }
        }
        WaterSession.adopt(ctx.applicationContext, jar)
        WaterDiag.log(ctx.applicationContext, "providerImport", WaterDiag.cookieSummary(jar))
        // 门锁跟着 cookie 一起进来的：钥匙是离线数据，不依赖 CM 校验
        env?.let {
            val applied = runCatching { LockImport.apply(ctx.applicationContext, it.doorlocks, it.mvLocks) }
                .getOrDefault(LockImport.Applied())
            if (applied.any) {
                WaterDiag.log(ctx.applicationContext, "providerLocks", applied.toString())
            }
        }
        return Bundle().apply { putBoolean("ok", true) }
    }

    /**
     * `call()` 由调用方线程（通常是主线程）同步执行，直接发网络会抛
     * `NetworkOnMainThreadException` —— 之前所有 provider 推送都被判「CM 层未绑定」就是这个原因。
     * 这里切到工作线程并有限等待。
     */
    private fun probeBlocking(jar: Map<String, String>): Boolean {
        // 一律丢到工作线程：本机型上 `call()` 实际跑在主线程，直接请求会抛
        // `NetworkOnMainThreadException`（诊断日志里复现过多次），
        // 于是每次推送都被判「CM 层未绑定」。
        var ok = false
        val latch = CountDownLatch(1)
        Thread {
            runCatching { ok = WaterSession.campusProbeOk(jar) }
            latch.countDown()
        }.apply { isDaemon = true; start() }
        latch.await(8, TimeUnit.SECONDS)
        return ok
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? = null
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null

    companion object {
        const val AUTHORITY = "com.fktm.app.cookies"
        const val METHOD_PUT = "put"
        const val EXTRA_JSON = "cookies_json"
    }
}
