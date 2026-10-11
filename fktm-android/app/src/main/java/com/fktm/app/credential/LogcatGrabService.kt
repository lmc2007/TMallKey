package com.fktm.app.credential

import com.fktm.app.ILogcatGrab
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 跑在 **Shizuku UserService 进程**里的抓取器（root 或 shell 身份）。
 *
 * 这个进程不是普通的 Android 应用进程 —— 它是 Shizuku 用 `app_process` 拉起来的，
 * 因此这里**不能**用 Context / ContentProvider 等 Android 组件（它们会失效）。
 * 也**不要**用 `android.util.Log`：`app_process` 下 native lib 路径可能不可用，
 * 会抛 UnsatisfiedLinkError。诊断信息统一攒进 [diagText]，由客户端取走。
 *
 * 但正因如此，它可以自由地用 [ProcessBuilder] 起 `logcat`：shell/root 身份本来就有权读日志。
 * 与 `fktm/p0_capture.bat` 做的事完全一致，只是从电脑搬到了手机里。
 *
 * ## 为什么要「命中即落盘」
 *
 * 抓取全程用户都在天猫校园里（fkTM 在后台），而 ColorOS 会把后台进程**冻结** ——
 * 实测连前台服务（dataSync 类型）也挡不住，App 会被 `do_freezer_trap` 冻住上百秒。
 * 冻结期间 binder 回复送不回来，App 里那个 `await` 一直不醒。
 * 所以：**只要命中，立刻把结果写进调用方给的文件**，由 App 在恢复前台时自取。
 * 这样结果绝不依赖「此刻 App 是否被调度」。
 */
class LogcatGrabService : ILogcatGrab.Stub() {

    /** 逐步更新的诊断（构造时先放哨兵值，便于判断「方法压根没被调到」）。 */
    @Volatile
    private var diagText: String = "grab() never called"

    private val notes = StringBuilder()

    override fun diag(): String = diagText

    /** 记录一步进度，并同步到远程可读的镜像文件（排障用）。 */
    @Synchronized
    private fun stage(text: String) {
        notes.append(text).append("; ")
        val snapshot = notes.toString()
        diagText = snapshot
        runCatching {
            File(MIRROR).writeText(snapshot)
            File(MIRROR).setReadable(true, false)
        }
    }

    override fun grab(timeoutMs: Int, resultPath: String): String {
        val t0 = System.currentTimeMillis()
        synchronized(this) { notes.setLength(0) }

        stage("t0")

        reapStaleSiblings()

        // 关键：UserService 进程的 PATH 可能不完整，`logcat` 未必能按名字找到。
        // 优先用绝对路径，失败再退回裸名字。
        val binary = resolveLogcat()
        if (binary == null) {
            stage("no logcat binary in PATH or /system/bin")
            return ""
        }
        stage("logcat=$binary")

        clearBuffer(binary)

        val proc = try {
            ProcessBuilder(binary, *LOGCAT_ARGS)
                .redirectErrorStream(true)
                .start()
        } catch (e: Throwable) {
            stage("exec failed: ${e.javaClass.simpleName}: ${e.message}")
            return ""
        }
        stage("logcat started")

        val buffer = StringBuilder()
        val done = CountDownLatch(1)
        val lineCount = AtomicInteger()
        val matchCount = AtomicInteger()
        val firstLines = AtomicReference<String?>(null)

        // logcat 是持续输出的阻塞流，丢给独立线程泵出；
        // 主线程只负责「等 latch 或被超时唤醒」。
        val reader = thread(name = "fktm-logcat", isDaemon = true) {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    val n = lineCount.incrementAndGet()
                    firstLines.compareAndSet(null, line)
                    if (n == 1) stage("first line arrived")
                    synchronized(buffer) { buffer.append(line).append('\n') }
                    if (LogcatParser.parse(line) != null) {
                        matchCount.incrementAndGet()
                        // ★ 命中即刻落盘：此后哪怕 App 被冻结/被杀，钥匙也不会丢。
                        stage("MATCH, ${writeHit(resultPath, line)}")
                        done.countDown()
                    }
                }
            } catch (_: Throwable) {
                // destroy() 之后 readLine 抛异常，属正常退出路径
            } finally {
                done.countDown()
            }
        }

        try {
            done.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // 读干净 stdout，否则强杀前那点错误输出会丢
        runCatching { proc.inputStream.close() }
        val exit = runCatching { proc.exitValue() }.getOrNull()
        runCatching { proc.destroy() }
        reader.interrupt()

        val elapsed = System.currentTimeMillis() - t0
        stage(
            "lines=${lineCount.get()} matched=${matchCount.get()}" +
                " exit=${exit ?: "still-running"} elapsed=${elapsed}ms"
        )
        firstLines.get()?.let { stage("first=${mask(it)}") }

        return synchronized(buffer) { buffer.toString() }
    }

    /**
     * 把命中的那一行**原样**写进 [resultPath]。
     *
     * 刻意不换成 JSON：写回的就是 logcat 的原始行，
     * 调用方可以直接复用同一个 [LogcatParser]（正则已单测覆盖），
     * 少一层格式转换就少一处会出错的地方。
     *
     * 先写临时文件再 rename，避免 App 读到写了一半的内容。
     */
    private fun writeHit(resultPath: String, line: String): String = try {
        val f = File(resultPath)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(line)
        tmp.setReadable(true, false)
        if (!tmp.renameTo(f)) {
            f.writeText(line)
            f.setReadable(true, false)
            runCatching { tmp.delete() }
        }
        "hit written"
    } catch (e: Throwable) {
        "hit write failed: ${e.javaClass.simpleName}: ${e.message}"
    }

    /** 清空日志缓冲，避免命中上一次（甚至换锁前）留下的旧记录。 */
    private fun clearBuffer(binary: String) {
        try {
            val p = ProcessBuilder(binary, "-c").redirectErrorStream(true).start()
            p.inputStream.readBytes()   // 读干净，防止缓冲区满阻塞
            p.waitFor()
            p.destroy()
            stage("buffer cleared")
        } catch (e: Throwable) {
            // 清不掉也不致命，继续抓 —— 但记下来，因为可能是权限问题的第一信号
            stage("clear failed: ${e.message}")
        }
    }

    /** `/system/bin/logcat` 优先；没有则退回 PATH 上的 `logcat`。 */
    private fun resolveLogcat(): String? {
        if (File(ABS_LOGCAT).canExecute()) return ABS_LOGCAT
        return "logcat"
    }

    /**
     * 回收**同名僵尸 UserService 进程**。
     *
     * 背景：Shizuku 的 UserService 进程不会随 `unbindUserService` 一起消亡 ——
     * 它会被 reparent 到 init（ppid=1）并一直挂着。
     * 换 APK 后新旧进程并存、日积月累；更糟的是 Shizuku 会按
     * `(包名, 后缀, version)` 复用旧进程，导致「改了代码没生效」。
     *
     * 这里趁我们已经起来，顺手把**cmdline 与本进程完全相同**的兄弟进程干掉。
     * 判据极窄（整段 cmdline 精确相等 + 排除自身），不会误伤别的进程。
     */
    private fun reapStaleSiblings() {
        val self = selfPid() ?: return
        val marker = selfCmdline()
        if (marker.isNullOrBlank()) return

        var reaped = 0
        runCatching {
            File(PROC).listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == self) return@forEach
                val cmd = runCatching {
                    File(dir, "cmdline").readBytes()
                        .toString(Charsets.ISO_8859_1)
                        .substringBefore('\u0000')
                }.getOrNull() ?: return@forEach
                if (cmd != marker) return@forEach
                runCatching {
                    ProcessBuilder(KILL, "-9", pid.toString())
                        .redirectErrorStream(true).start().waitFor()
                }
                reaped++
            }
        }
        if (reaped > 0) stage("reaped=$reaped")
    }

    /** `/proc/self` 是指向本进程 pid 目录的符号链接 → 解析出自身 pid。 */
    private fun selfPid(): Int? = runCatching {
        File("/proc/self").canonicalPath.substringAfterLast('/').toInt()
    }.getOrNull()

    /** 本进程的 cmdline（NUL 结尾，取第一段）。 */
    private fun selfCmdline(): String? = runCatching {
        File("/proc/self/cmdline").readBytes()
            .toString(Charsets.ISO_8859_1)
            .substringBefore('\u0000')
    }.getOrNull()

    /** 诊断信息里绝不能带出 activationKey —— 遮掉 hex 长串。 */
    private fun mask(line: String): String =
        line.replace(Regex("""[0-9a-fA-F]{16,}"""), "***")

    companion object {
        private const val ABS_LOGCAT = "/system/bin/logcat"
        private const val KILL = "/system/bin/kill"
        private const val PROC = "/proc"

        /**
         * 诊断镜像文件。写在 `/data/local/tmp`（shell 可读）——
         * 这样 App 被冻结时，我们用 `adb shell cat` 也能看到服务端走到哪一步了。
         */
        private const val MIRROR = "/data/local/tmp/fktm_grab_diag.txt"

        /** 与 `p0_capture.bat` 一致；`VocLockHelper` 匹配不到 `VocLockHelperV2`，两个都要列。 */
        private val TAGS = listOf(
            "VocLockHelperV2", "VocLockHelper", "VocLockJsBridge", "VocUnlockBridge",
            "BluetoothHelper", "VocLockManager", "VocLockSession", "VocLockBridge",
            "ZMUnlockBridge", "VocStatistics", "VocLogger", "Logger", "TLog",
        )

        private val LOGCAT_ARGS: Array<String> = buildList {
            add("-v"); add("time")
            TAGS.forEach { add("$it:V") }
            add("*:S")
        }.toTypedArray()
    }
}
