package com.fktm.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 通知权限（`POST_NOTIFICATIONS`）。
 *
 * 「常驻后台」靠的是前台服务（`GrabForegroundService`）—— 抓取期间 TmallKey 必须留在后台，
 * 否则会被 ColorOS 冻结、Shizuku 的 binder 回调直接断掉（已真机复现）。
 *
 * Android 13（API 33）起通知变成**运行时权限**：没有它前台服务照样能起来，
 * 但那条常驻通知不会显示 —— 用户既看不到「正在抓取」的进度，
 * 也没法从通知栏点回 App。所以凡是要起常驻服务的地方，都先用这里判断、缺了就申请。
 *
 * 同类的判断在 `BleLockClient.hasPermissions()` 里也有，那是蓝牙权限；
 * 通知权限跟蓝牙是两码事，单独放这里，避免以后改动互相牵连。
 */
object NotificationPermission {

    /** Android 13+ 才需要运行时申请；更低版本通知默认就是开的。 */
    val requiresRuntimeRequest: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 当前是否已具备通知权限（低版本恒为 true）。 */
    fun isGranted(context: Context): Boolean {
        if (!requiresRuntimeRequest) return true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        return granted == PackageManager.PERMISSION_GRANTED
    }

    /** 申请时用的权限名（低版本返回空串，调用方据此跳过申请）。 */
    val permission: String
        get() = if (requiresRuntimeRequest) Manifest.permission.POST_NOTIFICATIONS else ""
}
