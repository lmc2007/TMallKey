package com.fktm.app.vm

import com.fktm.app.water.WaterDevice
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterQuickUseTest {
    private val device = WaterDevice("A", "A", "", "Machine A", "", "", "", true, true, true, "", "")

    @Test fun quickUseChecksAndStartsSameDevice() = immediate {
        val flow = WaterQuickUse()
        val calls = mutableListOf<String>()
        val unlocked = flow.start("A", { calls += "check:$it"; device }, { calls += "start:$it" })
        assertEquals(listOf("check:A", "start:A"), calls)
        assertTrue(unlocked)
    }

    @Test fun unavailableOrMismatchedDeviceNeverStarts() = immediate {
        for (info in listOf(device.copy(online = false), device.copy(canUse = false),
            device.copy(inBusinessTime = false), device.copy(faultText = "fault"), device.copy(resNo = "B"))) {
            val flow = WaterQuickUse()
            val result = runCatching { flow.start("A", { info }, { error("Must not start") }) }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }
    }

    @Test fun rapidTapsStartAtMostOnce() = immediate {
        val flow = WaterQuickUse()
        lateinit var pending: Continuation<WaterDevice>
        val starts = mutableListOf<String>()
        val first: suspend () -> Unit = {
            flow.start("A", { suspendCoroutine { pending = it } }, { starts += it })
        }
        first.startCoroutine(continuation { it.getOrThrow() })
        assertFalse(flow.start("B", { error("Must not query another device") }, { error("Must not start") }))
        pending.resume(device)
        assertEquals(listOf("A"), starts)
    }

    @Test fun networkErrorsLeaveUnlockResultUnconfirmed() {
        assertTrue(WaterQuickUse.unlockUnconfirmed(com.fktm.app.water.MtopError(listOf("NETWORK_ERROR::timeout"))))
        assertTrue(WaterQuickUse.unlockUnconfirmed(java.io.IOException("missing response")))
        assertEquals(false, WaterQuickUse.unlockUnconfirmed(com.fktm.app.water.MtopError(listOf("FAIL_SYS_SESSION_EXPIRED::expired"))))
    }

    @Test fun completedUnlockAllowsAnotherWithoutEndingInTheApp() = immediate {
        val flow = WaterQuickUse()
        val unlocked = mutableListOf<String>()
        for (resNo in listOf("A", "B", "A")) {
            assertTrue(flow.start(resNo, { device.copy(resNo = it) }, { unlocked += it }))
        }
        assertEquals(listOf("A", "B", "A"), unlocked)
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
