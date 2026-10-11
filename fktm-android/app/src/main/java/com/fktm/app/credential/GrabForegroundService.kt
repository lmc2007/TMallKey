package com.fktm.app.credential

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.fktm.app.MainActivity
import com.fktm.app.R

/**
 * 抓取期间的**前台服务**。
 *
 * **为什么必须有它（这不是优化，是功能前提）：**
 * 抓取要求用户切到天猫校园去开一次锁 —— 也就是说抓取全程 fkTM 都在**后台**。
 * 而 OPPO/一加（ColorOS）这类系统会把后台进程**直接冻结**，真机日志里可见：
 *
 * ```
 * OplusHansManager: uid=10483, pkg=com.fktm.app M enter()
 * OplusHansManager: freeze uid: 10483 com.fktm.app pids: [xxx]
 * ```
 *
 * 冻结之后整条抓取链路一起停摆：
 *  · App 里的协程被掐停 —— 连 `withTimeoutOrNull` 的计时器都不走；
 *  · Shizuku 的 binder 回调无处投递，`grab()` 的返回值永远回不来；
 *  · 实测就是「点开始抓取 → 切天猫校园 → 开门 → 切回来还是没抓到」。
 *
 * 起一个前台服务把进程抬到 foreground 优先级，系统就不会冻结它。
 * 抓取结束（成功 / 失败 / 超时）立即停止，不留常驻通知。
 */
class GrabForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
            .onFailure { Log.w(TAG, "startForeground failed", it) }
        // 被抓取显式 stop；系统不要凭吊起的 intent 重启它
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.grab_notif_title))
            .setContentText(getString(R.string.grab_notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.grab_notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
    }

    companion object {
        private const val TAG = "fkTM"
        private const val CHANNEL_ID = "fktm_grab"
        private const val NOTIF_ID = 4207

        /**
         * 幂等启动。
         *
         * 必须在 App 仍处于前台时调用（Android 12+ 禁止后台启动前台服务）——
         * 调用点就在用户点「开始抓取」的那一刻，满足条件。
         */
        fun start(context: Context) {
            runCatching {
                Log.d(TAG, "foreground service: start")
                context.startForegroundService(Intent(context, GrabForegroundService::class.java))
            }.onFailure { Log.w(TAG, "startForegroundService failed", it) }
        }

        fun stop(context: Context) {
            runCatching {
                Log.d(TAG, "foreground service: stop")
                context.stopService(Intent(context, GrabForegroundService::class.java))
            }
        }
    }
}
