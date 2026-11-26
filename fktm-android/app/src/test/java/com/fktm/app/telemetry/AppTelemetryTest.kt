package com.fktm.app.telemetry

import com.fktm.app.water.WaterDeviceSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTelemetryTest {
    @Test fun eventsUseFixedNamesAndProperties() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val telemetry = AppTelemetry { event, properties -> events += event to properties }

        telemetry.appStarted()
        telemetry.screenViewed(AppTelemetry.Screen.WATER)
        telemetry.waterUnlockResult(AppTelemetry.WaterUnlockResult.UNCONFIRMED)
        telemetry.waterDeviceAdded(AppTelemetry.AddMethod.QR, success = false)
        telemetry.waterSortChanged(WaterDeviceSortMode.CUSTOM)
        telemetry.doorUnlockResult(AppTelemetry.DoorMode.MV, AppTelemetry.DoorSource.SHORTCUT, success = true)

        assertEquals(
            listOf(
                "app_started" to emptyMap(),
                "screen_view" to mapOf("name" to "water"),
                "water_unlock" to mapOf("result" to "unconfirmed"),
                "water_device_add" to mapOf("method" to "qr", "result" to "failed"),
                "water_sort_mode_changed" to mapOf("mode" to "custom"),
                "door_unlock" to mapOf("mode" to "mv", "source" to "shortcut", "result" to "command_sent"),
            ),
            events,
        )
    }

    @Test fun telemetryFailureDoesNotInterruptAppActions() {
        val telemetry = AppTelemetry { _, _ -> throw IllegalStateException("SDK unavailable") }
        telemetry.waterUnlockResult(AppTelemetry.WaterUnlockResult.SUCCESS)
    }
}
