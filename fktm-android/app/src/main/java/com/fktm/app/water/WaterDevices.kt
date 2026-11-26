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
    private const val SORT_MODE = "sort_mode"
    private const val CUSTOM_ORDER = "custom_order"

    data class Saved(
        val resNo: String,
        val deviceId: String,
        val name: String,
        val location: String,
        val lastUsedAt: Long,
    )

    fun list(context: Context): List<Saved> {
        val devices = readDevices(context)
        if (sortMode(context) == WaterDeviceSortMode.RECENT) return WaterDeviceOrder.recent(devices)
        val byId = devices.associateBy { it.resNo }
        val ids = readCustomOrder(context) ?: WaterDeviceOrder.recent(devices).map { it.resNo }
        return WaterDeviceOrder.customIds(devices, ids).map(byId::getValue)
    }

    fun sortMode(context: Context): WaterDeviceSortMode =
        WaterDeviceOrder.mode(prefs(context).getString(SORT_MODE, null))

    fun setSortMode(context: Context, mode: WaterDeviceSortMode) {
        val editor = prefs(context).edit().putString(SORT_MODE, mode.name)
        if (mode == WaterDeviceSortMode.CUSTOM && readCustomOrder(context) == null) {
            val ids = WaterDeviceOrder.recent(readDevices(context)).map { it.resNo }
            editor.putString(CUSTOM_ORDER, JSONArray(ids).toString())
        }
        editor.apply()
    }

    fun saveCustomOrder(context: Context, resNos: List<String>) {
        val ids = WaterDeviceOrder.customIds(readDevices(context), resNos)
        prefs(context).edit().putString(CUSTOM_ORDER, JSONArray(ids).toString()).apply()
    }

    private fun readCustomOrder(context: Context): List<String>? {
        val raw = prefs(context).getString(CUSTOM_ORDER, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.optString(it) }
        }.getOrNull()
    }

    private fun readDevices(context: Context): List<Saved> {
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
            }.distinctBy { it.resNo }.take(WaterDeviceOrder.MAX_DEVICES)
        }.getOrElse { emptyList() }
    }

    /** 扫到一台设备就记下来（已有的更新信息，不动使用时间）。 */
    fun remember(context: Context, device: WaterDevice): Boolean {
        val list = readDevices(context).toMutableList()
        val idx = list.indexOfFirst { it.resNo == device.resNo }
        if (!WaterDeviceOrder.canRemember(list, device.resNo)) return false
        val old = if (idx >= 0) list[idx] else null
        val entry = Saved(
            resNo = device.resNo,
            deviceId = device.deviceId.ifBlank { old?.deviceId.orEmpty() },
            name = device.name.ifBlank { old?.name ?: "饮水机" },
            location = listOfNotNull(
                device.building.ifBlank { null },
                device.address.ifBlank { null },
            ).joinToString(" · ").ifBlank { old?.location.orEmpty() },
            lastUsedAt = old?.lastUsedAt ?: 0L,
        )
        if (idx >= 0) list[idx] = entry else list.add(entry)
        save(context, list)
        Log.d(TAG, "water device remembered: ${entry.resNo}")
        return true
    }

    /** 解锁成功时刷新使用时间。 */
    fun touch(context: Context, resNo: String) {
        val list = readDevices(context).toMutableList()
        val idx = list.indexOfFirst { it.resNo == resNo }
        if (idx < 0) return
        list[idx] = list[idx].copy(lastUsedAt = System.currentTimeMillis())
        save(context, list)
    }

    fun remove(context: Context, resNo: String) {
        save(context, readDevices(context).filterNot { it.resNo == resNo })
    }

    private fun save(context: Context, list: List<Saved>) {
        val arr = JSONArray()
        list.forEach { d ->
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
        val editor = prefs(context).edit().putString(KEY, arr.toString())
        readCustomOrder(context)?.let { ids ->
            editor.putString(CUSTOM_ORDER, JSONArray(WaterDeviceOrder.customIds(list, ids)).toString())
        }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}

enum class WaterDeviceSortMode { RECENT, CUSTOM }

internal object WaterDeviceOrder {
    const val MAX_DEVICES = 30

    fun mode(value: String?): WaterDeviceSortMode =
        WaterDeviceSortMode.entries.firstOrNull { it.name == value } ?: WaterDeviceSortMode.RECENT

    fun recent(devices: List<WaterDevices.Saved>): List<WaterDevices.Saved> =
        devices.sortedByDescending { it.lastUsedAt }

    fun customIds(devices: List<WaterDevices.Saved>, preferred: List<String>): List<String> {
        val existing = devices.map { it.resNo }.toSet()
        return (preferred.filter { it in existing } + existing).distinct()
    }

    fun canRemember(devices: List<WaterDevices.Saved>, resNo: String): Boolean =
        devices.size < MAX_DEVICES || devices.any { it.resNo == resNo }
}
