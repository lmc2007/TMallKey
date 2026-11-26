package com.fktm.app.vm

import com.fktm.app.water.WaterDevice
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterQuickUseTest {
    private val device = WaterDevice("A", "A", "", "Machine A", "", "", "", true, true, true, "", "")

    @Test fun quickUseChecksAndStartsSameDevice() = immediate {
        val flow = WaterQuickUse()
        val calls = mutableListOf<String>()
        val active = flow.start("A", { calls += "check:$it"; device }, { calls += "start:$it"; "order-A" })
        assertEquals(listOf("check:A", "start:A"), calls)
        assertEquals("order-A", active?.orderId)
        assertSame(active, flow.active)
    }

    @Test fun unavailableOrMismatchedDeviceNeverStarts() = immediate {
        for (info in listOf(device.copy(online = false), device.copy(canUse = false),
            device.copy(inBusinessTime = false), device.copy(faultText = "fault"), device.copy(resNo = "B"))) {
            val flow = WaterQuickUse()
            val result = runCatching { flow.start("A", { info }, { error("Must not start") }) }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertNull(flow.active)
        }
    }

    @Test fun rapidTapsStartAtMostOnce() = immediate {
        val flow = WaterQuickUse()
        lateinit var pending: Continuation<WaterDevice>
        val starts = mutableListOf<String>()
        val first: suspend () -> Unit = {
            flow.start("A", { suspendCoroutine { pending = it } }, { starts += it; "order-A" })
        }
        first.startCoroutine(continuation { it.getOrThrow() })
        assertNull(flow.start("B", { error("Must not query another device") }, { error("Must not start") }))
        pending.resume(device)
        assertEquals(listOf("A"), starts)
        assertNull(flow.start("B", { error("Must not query during an order") }, { error("Must not start") }))
    }

    @Test fun failedStopRetainsActiveDevice() = immediate {
        val flow = WaterQuickUse()
        val active = flow.start("A", { device }, { "order-A" })
        val failure = IllegalStateException("stop failed")
        val result = runCatching { flow.stop { _, _ -> throw failure } }
        assertSame(failure, result.exceptionOrNull())
        assertSame(active, flow.active)
        flow.stop { resNo, id -> assertEquals("A", resNo); assertEquals("order-A", id) }
        assertNull(flow.active)
    }

    @Test fun successfulUnlockWithoutOrderIdCanStillEnd() = immediate {
        val flow = WaterQuickUse()
        flow.start("A", { device }, { "" })
        assertEquals("A", flow.active?.device?.resNo)
        flow.stop { resNo, id -> assertEquals("A", resNo); assertEquals("", id) }
        assertNull(flow.active)
    }

    @Test fun recoveryOnlyAcceptsTheOriginalAccount() {
        val active = WaterQuickUse.Active(device, "order-A", "account-A")
        assertTrue(active.canRecoverWith("account-A"))
        assertEquals(false, active.canRecoverWith("account-B"))
        assertEquals(false, active.canRecoverWith(""))
        assertEquals(false, active.copy(accountId = "").canRecoverWith(""))
    }

    @Test fun networkErrorsLeaveUnlockResultUnconfirmed() {
        assertTrue(WaterQuickUse.unlockUnconfirmed(com.fktm.app.water.MtopError(listOf("NETWORK_ERROR::timeout"))))
        assertTrue(WaterQuickUse.unlockUnconfirmed(java.io.IOException("missing response")))
        assertEquals(false, WaterQuickUse.unlockUnconfirmed(com.fktm.app.water.MtopError(listOf("FAIL_SYS_SESSION_EXPIRED::expired"))))
    }

    @Test fun confirmedPhysicalEndClearsOnlyTheLocalOrder() = immediate {
        val flow = WaterQuickUse()
        flow.start("A", { device }, { "order-A" })
        runCatching { flow.stop { _, _ -> error("stop failed") } }
        assertEquals("order-A", flow.active?.orderId)
        flow.confirmPhysicalEnd()
        assertNull(flow.active)
    }

    private fun immediate(block: suspend () -> Unit) {
        var completed = false
        block.startCoroutine(continuation { it.getOrThrow(); completed = true })
        assertTrue("Test suspended unexpectedly", completed)
    }

    private fun continuation(complete: (Result<Unit>) -> Unit) = object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) = complete(result)
    }
}
