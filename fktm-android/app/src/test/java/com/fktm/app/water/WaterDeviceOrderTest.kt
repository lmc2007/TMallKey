package com.fktm.app.water

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterDeviceOrderTest {
    private fun device(id: String, lastUsed: Long = 0) =
        WaterDevices.Saved(id, "", id, "", lastUsed)

    @Test fun legacyRecordsUseRecentOrder() {
        assertEquals(WaterDeviceSortMode.RECENT, WaterDeviceOrder.mode(null))
        assertEquals(WaterDeviceSortMode.RECENT, WaterDeviceOrder.mode("invalid"))
        val devices = listOf(device("A", 1), device("B", 2), device("C", 1))
        assertEquals(listOf("B", "A", "C"), WaterDeviceOrder.recent(devices).map { it.resNo })
    }

    @Test fun customOrderSurvivesModeRoundTrip() {
        val devices = listOf(device("A", 1), device("B", 3))
        val saved = listOf("A", "B")
        assertEquals(listOf("B", "A"), WaterDeviceOrder.recent(devices).map { it.resNo })
        assertEquals(saved, WaterDeviceOrder.customIds(devices, saved))
    }

    @Test fun customOrderAppendAndRemove() {
        val saved = listOf("B", "A")
        val devices = listOf(device("A"), device("B"), device("C", 10))
        assertEquals(listOf("B", "A", "C"), WaterDeviceOrder.customIds(devices, saved))
        assertEquals(listOf("B", "C"), WaterDeviceOrder.customIds(devices.drop(1), saved))
    }

    @Test fun customOrderRepairsDuplicateOrStaleIds() {
        val devices = listOf(device("A"), device("B"))
        assertEquals(listOf("B", "A"), WaterDeviceOrder.customIds(devices, listOf("B", "B", "Z")))
    }

    @Test fun deviceLimitAllowsUpdatingExistingDevice() {
        val devices = (1..30).map { device(it.toString()) }
        assertEquals(false, WaterDeviceOrder.canRemember(devices, "31"))
        assertEquals(true, WaterDeviceOrder.canRemember(devices, "30"))
    }
}
