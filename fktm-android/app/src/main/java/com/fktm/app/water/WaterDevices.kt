package com.fktm.app.water

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 扫过的饮水机（本机保存，最近使用的排最前）。
 *
 * 只存公开信息：资源号、设备 ID、名称、位置、上次使用时间。
 * **不存任何凭据**，凭据在 [WaterCookies] 那套 Keystore 加密里。
 */
object WaterDevices {

    private const val TAG = "fkTM"
    private const val PREF = "water_devices"
    private const val KEY = "list_v1"

    /** 最多保留多少台，防止无限增长。 */
    private const val MAX = 30

    data class Saved(
        val resNo: String,
        val deviceId: String,
        val name: String,
        val location: String,
        val lastUsedAt: Long,
    )

    /** 按「最近使用」倒序。 */
    fun list(context: Context): List<Saved> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val resNo = o.optString("resNo").ifBlank { return@mapNotNull null }
                Saved(
                    resNo = resNo,
                    deviceId = o.optString("deviceId"),
                    name = o.optString("name").ifBlank { "饮水机" },
                    location = o.optString("location"),
                    lastUsedAt = o.optLong("lastUsedAt"),
                )
            }.sortedByDescending { it.lastUsedAt }
        }.getOrElse { emptyList() }
    }

    /** 扫到一台设备就记下来（已有的更新信息，不动使用时间）。 */
    fun remember(context: Context, device: WaterDevice, usedAt: Long = 0L) {
        val list = list(context).toMutableList()
        val idx = list.indexOfFirst { it.resNo == device.resNo }
        val old = if (idx >= 0) list[idx] else null
        val entry = Saved(
            resNo = device.resNo,
            deviceId = device.deviceId.ifBlank { old?.deviceId.orEmpty() },
            name = device.name.ifBlank { old?.name ?: "饮水机" },
            location = listOfNotNull(
                device.building.ifBlank { null },
                device.address.ifBlank { null },
            ).joinToString(" · ").ifBlank { old?.location.orEmpty() },
            lastUsedAt = maxOf(old?.lastUsedAt ?: 0L, usedAt),
        )
        if (idx >= 0) list[idx] = entry else list.add(entry)
        save(context, list)
        Log.d(TAG, "water device remembered: ${entry.resNo}")
    }

    /** 解锁成功时把时间戳刷新，自动置顶。 */
    fun touch(context: Context, resNo: String) {
        val list = list(context).toMutableList()
        val idx = list.indexOfFirst { it.resNo == resNo }
        if (idx < 0) return
        list[idx] = list[idx].copy(lastUsedAt = System.currentTimeMillis())
        save(context, list)
    }

    fun remove(context: Context, resNo: String) {
        save(context, list(context).filterNot { it.resNo == resNo })
    }

    private fun save(context: Context, list: List<Saved>) {
        val arr = JSONArray()
        list.sortedByDescending { it.lastUsedAt }.take(MAX).forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("resNo", d.resNo)
                    put("deviceId", d.deviceId)
                    put("name", d.name)
                    put("location", d.location)
                    put("lastUsedAt", d.lastUsedAt)
                }
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 「3 分钟前」这种。 */
    fun relativeTime(ms: Long): String {
        if (ms <= 0) return ""
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
            else -> "${diff / (30 * 86_400_000L)} 个月前"
        }
    }
}
