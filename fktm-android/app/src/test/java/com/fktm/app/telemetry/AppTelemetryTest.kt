package com.fktm.app.telemetry

import com.fktm.app.water.WaterDeviceSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTelemetryTest {
    @Test fun eventsUseFixedNamesAndProperties() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val telemetry = AppTelemetry(isEnabled = { true }) { event, properties -> events += event to properties }

        telemetry.appStarted()
        telemetry.waterUnlockResult(AppTelemetry.WaterUnlockResult.UNCONFIRMED)
        telemetry.waterDeviceAdded(AppTelemetry.AddMethod.QR, success = false)
        telemetry.waterSortChanged(WaterDeviceSortMode.CUSTOM)
        telemetry.doorUnlockResult(AppTelemetry.DoorMode.MV, AppTelemetry.DoorSource.SHORTCUT, success = true)

        assertEquals(
            listOf(
                "app_started" to emptyMap(),
                "water_unlock" to mapOf("result" to "unconfirmed"),
                "water_device_add" to mapOf("method" to "qr", "result" to "failed"),
                "water_sort_mode_changed" to mapOf("mode" to "custom"),
                "door_unlock" to mapOf("mode" to "mv", "source" to "shortcut", "result" to "command_sent"),
            ),
            events,
        )
    }

    @Test fun telemetryFailureDoesNotInterruptAppActions() {
        val telemetry = AppTelemetry(isEnabled = { true }) { _, _ -> throw IllegalStateException("SDK unavailable") }
        telemetry.waterUnlockResult(AppTelemetry.WaterUnlockResult.SUCCESS)
    }

    @Test fun disablingTelemetryDropsAllEventsUntilReenabled() {
        var enabled = true
        val events = mutableListOf<String>()
        val telemetry = AppTelemetry(isEnabled = { enabled }) { event, _ -> events += event }

        telemetry.appStarted()
        enabled = false
        telemetry.appStarted()
        telemetry.waterUnlockResult(AppTelemetry.WaterUnlockResult.FAILED)
        telemetry.waterDeviceAdded(AppTelemetry.AddMethod.MANUAL, success = true)
        telemetry.waterSortChanged(WaterDeviceSortMode.RECENT)
        telemetry.doorUnlockResult(AppTelemetry.DoorMode.VOC, AppTelemetry.DoorSource.LIST, success = false)
        assertEquals(listOf("app_started"), events)

        enabled = true
        telemetry.waterSortChanged(WaterDeviceSortMode.CUSTOM)
        assertEquals(listOf("app_started", "water_sort_mode_changed"), events)
    }
}
