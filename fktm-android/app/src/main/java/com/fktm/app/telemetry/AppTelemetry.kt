package com.fktm.app.telemetry

import com.aptabase.Aptabase
import com.fktm.app.water.WaterDeviceSortMode

/** The event schema accepts only fixed values, so device and account data stay in app code. */
internal class AppTelemetry(private val send: (String, Map<String, String>) -> Unit) {
    enum class Screen(val tabIndex: Int) { DOOR_LOCKS(0), WATER(1), ABOUT(2) }
    enum class WaterUnlockResult { SUCCESS, FAILED, UNCONFIRMED }
    enum class AddMethod { QR, MANUAL }
    enum class DoorMode { VOC, MV }
    enum class DoorSource { LIST, SHORTCUT }

    fun appStarted() = track("app_started")

    fun screenViewed(screen: Screen) = track("screen_view", "name" to screen.name.lowercase())

    fun waterUnlockResult(result: WaterUnlockResult) = track("water_unlock", "result" to result.name.lowercase())

    fun waterDeviceAdded(method: AddMethod, success: Boolean) = track(
        "water_device_add",
        "method" to method.name.lowercase(),
        "result" to if (success) "success" else "failed",
    )

    fun waterSortChanged(mode: WaterDeviceSortMode) = track(
        "water_sort_mode_changed",
        "mode" to mode.name.lowercase(),
    )

    fun doorUnlockResult(mode: DoorMode, source: DoorSource, success: Boolean) {
        val result = when {
            !success -> "failed"
            mode == DoorMode.MV -> "command_sent"
            else -> "success"
        }
        track("door_unlock", "mode" to mode.name.lowercase(), "source" to source.name.lowercase(), "result" to result)
    }

    private fun track(event: String, vararg properties: Pair<String, String>) {
        try {
            send(event, properties.toMap())
        } catch (_: Exception) {
            // A telemetry failure must not interrupt an app action.
        }
    }

    companion object {
        val instance = AppTelemetry { event, properties -> Aptabase.instance.trackEvent(event, properties) }
    }
}
