package com.fktm.app.credential

import android.util.Log
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
 *
 * ⚠️ **必须先 [capture] 再抓 cookie**（2026-11-06 回归）：
 * 天猫校园刷屏极凶，`VocLockHelperV2` 的日志在环形缓冲里活不过 60 s。
 * 旧代码把 cookie 抓取（含 `campusProbeOk` 网络探测，无网时等超时）排在 capture 前面，
 * logcat 晚几十秒才挂上 —— 用户点完开锁回来，日志早被冲没了。
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

    /**
     * 默认窗口 15 min。
     *
     * `reconnect()` 不打 key 日志：天猫校园进程活着的期间会话一直缓存着，
     * 第二次及以后点开锁走 `reconnect()`，日志里只有 `onDeviceFound/connect`，**永远没有**
     * `activationKey`。所以先 [scanHistory] 把环形缓冲里那一次真 `unlock()` 捞出来。
     */
    suspend fun capture(timeoutMs: Long = 900_000L): Outcome =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "grab: capture start, timeout=${timeoutMs}ms")

            // ① 先扫历史：用户此刻大概率是「刚开过锁」，live 日志可能已滚掉。
            scanHistory()?.let {
                Log.d(TAG, "grab: history hit -> ${describe(it)}")
                return@withContext it
            }
            Log.d(TAG, "grab: history miss, fallback to streaming")

            // ② 再流式追新日志（首次开锁 / 天猫校园刚重启的场景）
            //
            // ⚠️ 必须经 busybox `script` 套一层 pty（2026-11-06 实测根因）：
            // logcat 的 stdout 一旦是 Pipe（ProcessBuilder 就是），bionic stdio 走 4 KB 全缓冲，
            // `-T 1` 初始 dump 之后 follow 的输出全部卡在用户态缓冲里，永远不 flush。
            // 现场证据：`/proc/<pid>/fd/1 -> pipe:[323568]`，子进程活着、天猫校园明明打了
            // `unlock: … activationKey:`，Java 侧一行都收不到。
            // 塞进 pty 后 stdio 变行缓冲，数据立刻通过管道流出来（已用 shell 等价管道验证）。
            // 没有 busybox 时退化为裸 logcat —— 只能拿到 `-T 1` 的初始 dump，
            // 但配合下面的 [scanHistory] 通常也够。
            val inner = "logcat -T 1 -s VocLockHelperV2:V FastBle:V"
            val cmd = "BB=\$(command -v busybox 2>/dev/null); " +
                "[ -x \"\$BB\" ] || BB=/data/adb/ksu/bin/busybox; " +
                "[ -x \"\$BB\" ] || BB=/data/adb/magisk/busybox; " +
                "if [ -x \"\$BB\" ]; then exec \$BB script -q -c \"$inner\" /dev/null; fi; " +
                "exec $inner"
            Log.d(TAG, "grab: cmd=$cmd")
            val proc = ProcessBuilder("su", "-M", "-c", cmd)
                .redirectErrorStream(false)
                .start()
            Log.d(TAG, "grab: streaming up")
            try {
                val out = withTimeoutOrNull(timeoutMs) {
                    proc.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            // VOC 优先：`FastBle` 的 MAC 正则太宽，别让它抢答
                            if ("VocLockHelperV2" in line) {
                                Log.d(TAG, "grab: line ${redact(line)}")
                                LogcatParser.parse(line)?.let {
                                    return@withTimeoutOrNull Outcome.VocHit(it)
                                }
                            } else if ("FastBle" in line) {
                                Log.d(TAG, "grab: line ${redact(line)}")
                                // MV（掌门神通）必须能确认是它：设备名前缀 MVL，或 AFF0/1/2 服务特征。
                                // 否则 MAC_RE 会把任何 6 段 hex 都当成 MAC —— 误判的代价是把 VOC 用户
                                // 导进一条没有钥匙的垃圾 MV 凭证，开锁必然失败。宁可漏判。
                                if (MV_HINT.containsMatchIn(line)) {
                                    MAC_RE.find(line)?.let { m ->
                                        return@withTimeoutOrNull Outcome.MvHit(m.value.uppercase())
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                        null
                    }
                }
                if (out == null) Log.d(TAG, "grab: streaming timeout after ${timeoutMs}ms")
                out ?: Outcome.None
            } finally {
                runCatching { proc.destroy() }
            }
        }

    /**
     * 扫一遍 logcat 环形缓冲里**已经存在**的 VOC 日志。
     *
     * `-d`（dump 完退出）+ 不带 `-T`：拿缓冲全量。
     * 整段读完再交给 [LogcatParser.parse] 取「最后一条」，避免命中缓冲里最早的旧锁
     * —— 逐行命中即返回会拿到最老那条，可能是别人的锁。
     *
     * 只搜 `VocLockHelperV2`：历史里的 `FastBle` 连接记录分不清是哪把 MV 锁，留给流式阶段。
     */
    private fun scanHistory(): Outcome? = runCatching {
        val started = System.currentTimeMillis()
        val proc = ProcessBuilder("su", "-M", "-c", "logcat -d -s VocLockHelperV2:V")
            .redirectErrorStream(false)
            .start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroy()
        val cost = System.currentTimeMillis() - started
        val hit = LogcatParser.parse(text)
        Log.d(
            TAG,
            "grab: history ${text.length}B ${text.lineSequence().count()}L ${cost}ms " +
                "hit=${hit != null}",
        )
        hit?.let { Outcome.VocHit(it) }
    }.getOrNull()

    private fun describe(o: Outcome): String = when (o) {
        is Outcome.VocHit -> "VOC mac=${o.hit.macAddress} complete=${o.hit.isComplete}"
        is Outcome.MvHit -> "MV mac=${o.mac}"
        Outcome.None -> "none"
    }

    /** 日志里出现的 32 hex 就是钥匙，别原样写进 logcat。 */
    private fun redact(s: String): String =
        HEX_RUN.replace(s) { m ->
            val v = m.value
            "${v.take(6)}…${v.takeLast(4)}(len=${v.length})"
        }

    private val HEX_RUN = Regex("""[0-9a-fA-F]{16,}""")
    private val MV_HINT = Regex("""MVL|AFF[0-2]|zmst""", RegexOption.IGNORE_CASE)
    private val MAC_RE = Regex("""\b[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}\b""")
}
