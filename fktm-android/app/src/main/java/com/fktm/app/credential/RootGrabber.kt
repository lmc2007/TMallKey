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
     * 流式监听 logcat，VOC/MV 谁先出现算谁。
     *
     *  · VOC：`VocLockHelperV2` 打出 `unlock: macAddress… activationKey…` → 解析出钥匙
     *  · MV：`FastBle` 打出 `mac: XX:XX…`（掌门神通锁连接事件，设备名 MVL 开头）
     *    —— MV 没有静态钥匙，抓到 MAC 即可（开锁指令由 fkTM 联网现取）
     */
    sealed interface Outcome {
        data class VocHit(val hit: LogcatParser.Hit) : Outcome
        data class MvHit(val mac: String) : Outcome
        data object None : Outcome
    }

    suspend fun capture(timeoutMs: Long = 240_000L): Outcome =
        withContext(Dispatchers.IO) {
            val proc = ProcessBuilder(
                "su", "-M", "-c",
                "logcat -T 1 -s VocLockHelperV2:V FastBle:V",
            )
                .redirectErrorStream(false)
                .start()
            try {
                withTimeoutOrNull(timeoutMs) {
                    proc.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            if ("FastBle" in line) {
                                MAC_RE.find(line)?.let { m ->
                                    return@withTimeoutOrNull Outcome.MvHit(
                                        m.value.uppercase(),
                                    )
                                }
                            } else if ("VocLockHelperV2" in line) {
                                LogcatParser.parse(line)?.let { return@withTimeoutOrNull Outcome.VocHit(it) }
                            }
                            line = reader.readLine()
                        }
                        null
                    }
                } ?: Outcome.None
            } finally {
                runCatching { proc.destroy() }
            }
        }

    private val MAC_RE = Regex("""\b[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}\b""")
}
