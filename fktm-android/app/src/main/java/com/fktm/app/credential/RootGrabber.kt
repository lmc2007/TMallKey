package com.fktm.app.credential

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * root 一键抓取：不出 App 进程，`su -M` 直接干两件事 ——
 *
 * ① 读天猫校园 WebView cookie 库（登录态，见 [com.fktm.app.water.WaterSession]）
 * ② 流式监听 logcat 的 `VocLockHelperV2` 标签，命中 activationKey 即返回
 *
 * `-M`（--mount-master）必不可少：App 内的 su 跑在自己的 mount namespace 里，
 * 看不见别的应用数据（实测 2026-10-21）。
 */
object RootGrabber {

    private const val TAG = "fkTM"

    /** 授权框可能要人点，留够时间。 */
    fun hasRoot(): Boolean = runCatching {
        val proc = ProcessBuilder("su", "-M", "-c", "id -u")
            .redirectErrorStream(false)
            .start()
        val done = proc.waitFor(20, TimeUnit.SECONDS)
        val out = if (done) proc.inputStream.bufferedReader().readText().trim() else ""
        proc.destroy()
        done && out == "0"
    }.getOrDefault(false)

    /**
     * 流式监听 logcat，命中 activationKey 即返回。
     *
     * `logcat -T 1` 只看新日志（避免历史里旧钥匙误命中）；命中前一直挂着，
     * 用户切到天猫校园开一次门即可。硬超时 [timeoutMs]。
     */
    suspend fun captureKey(timeoutMs: Long = 240_000L): LogcatParser.Hit? =
        withContext(Dispatchers.IO) {
            val proc = ProcessBuilder("su", "-M", "-c", "logcat -T 1 -s VocLockHelperV2:V")
                .redirectErrorStream(false)
                .start()
            try {
                withTimeoutOrNull(timeoutMs) {
                    proc.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            LogcatParser.parse(line)?.let { return@withTimeoutOrNull it }
                            line = reader.readLine()
                        }
                        null
                    }
                }
            } finally {
                runCatching { proc.destroy() }
            }
        }
}
