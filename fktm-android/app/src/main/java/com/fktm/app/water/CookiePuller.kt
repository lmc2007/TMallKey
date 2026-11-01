package com.fktm.app.water

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 从（LSPatch 补丁过的）天猫校园进程里**拉**数据。
 *
 * ## 为什么是拉而不是等推送
 *
 * 模块跑在天猫校园进程里，cookie 对它来说就是自己的 `CookieManager`。
 * 但要把数据交给 fkTM，三种通道都不行：
 *  · 广播     → fkTM 在后台被 ColorOS 冻结时收不到（实测）
 *  · Provider → Android 11+ 包可见性：天猫校园没在清单里声明 `com.fktm.app`，
 *               AMS 直接回 `Unknown authority`（实测）
 *  · 写文件   → fkTM 读不了别人的私有目录（除非 root）
 *
 * 所以反过来：**模块开一个本地回环 Socket（127.0.0.1:42741）**，
 * fkTM 打开饮水页时自己连上去拉。回环端口不经过任何权限/可见性检查。
 *
 * 安全：连接时要先发一个共享 token，防止其它本地 App 误连拿走 cookie；
 * 这只是防误连，不是强加密边界（token 写死在两个 App 里）。
 */
object CookiePuller {

    private const val TAG = "fkTM"
    private const val PORT = 42741
    private const val TOKEN = "fktm-cookie-pull-v1"

    data class PullResult(
        val cookies: Map<String, String>,
        val doorlocks: List<ModuleEnvelope.DoorLock>,
        val mvLocks: List<ModuleEnvelope.MvLock> = emptyList(),
    )

    /**
     * 尝试拉取一次。阻塞调用，请在 IO 线程执行。
     *
     * @return null 表示天猫校园没在跑 / 没登录 / 没开模块
     */
    fun pull(): PullResult? = runCatching {
        Socket().use { s ->
            // 显式 IPv4，与服务端一致（getLoopbackAddress 可能返回 ::1）
            s.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 2000)
            s.soTimeout = 2000
            val out = s.getOutputStream()
            out.write((TOKEN + "\n").toByteArray())
            out.flush()
            val line = s.getInputStream().bufferedReader().readLine()?.trim().orEmpty()
            if (!line.startsWith("{")) return@runCatching null

            val env = ModuleEnvelope.parse(line) ?: return@runCatching null
            if (WaterSession.accountId(env.cookies).isBlank()) return@runCatching null

            PullResult(env.cookies, env.doorlocks, env.mvLocks)
        }
    }.getOrElse { e ->
        Log.d(TAG, "cookie pull: ${e.javaClass.simpleName}")
        null
    }
}
