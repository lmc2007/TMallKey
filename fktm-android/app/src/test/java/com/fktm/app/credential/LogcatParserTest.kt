package com.fktm.app.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LogcatParser 回归。
 *
 * 样本按真实 logcat 行格式构造（时间戳 + pid + tag 前缀），
 * 但 key 全部是假值 —— 测试文件里不放真钥匙。
 */
class LogcatParserTest {

    private val fakeKey = "A1B2C3D4E5F60718293A4B5C6D7E8F90"
    private val fakeMac = "AA:BB:CC:DD:EE:FF"

    private fun line(body: String) =
        "10-11 18:15:03.123  4821  4865 D VocLockHelperV2: $body"

    @Test
    fun `parses the real line shape`() {
        val log = line("unlock:  macAddress: $fakeMac activationKey: $fakeKey")

        val hit = LogcatParser.parse(log)

        assertEquals(fakeKey, hit?.activationKey)
        assertEquals(fakeMac, hit?.macAddress)
        assertTrue(hit!!.isComplete)
    }

    @Test
    fun `uppercases a lowercase key and mac`() {
        val log = line("unlock:  macAddress: $fakeMac activationKey: ${fakeKey.lowercase()}")

        val hit = LogcatParser.parse(log)

        assertEquals(fakeKey, hit?.activationKey)
        assertEquals(fakeMac, hit?.macAddress)
    }

    @Test
    fun `accepts equals sign as separator`() {
        val hit = LogcatParser.parse("macAddress=$fakeMac activationKey=$fakeKey")

        assertEquals(fakeKey, hit?.activationKey)
        assertEquals(fakeMac, hit?.macAddress)
    }

    @Test
    fun `keeps going when mac sits on an earlier line`() {
        val log = buildString {
            appendLine(line("macAddress: $fakeMac"))
            appendLine(line("activationKey: $fakeKey"))
        }

        val hit = LogcatParser.parse(log)

        assertEquals(fakeKey, hit?.activationKey)
        assertEquals(fakeMac, hit?.macAddress)
    }

    @Test
    fun `returns the last hit when several locks appear`() {
        val otherKey = "00000000000000000000000000000001"
        val log = buildString {
            appendLine(line("macAddress: 11:22:33:44:55:66 activationKey: $otherKey"))
            appendLine(line("macAddress: $fakeMac activationKey: $fakeKey"))
        }

        val hit = LogcatParser.parse(log)

        assertEquals(fakeKey, hit?.activationKey)
        assertEquals(fakeMac, hit?.macAddress)
    }

    @Test
    fun `key without mac is still a hit but flagged incomplete`() {
        val hit = LogcatParser.parse(line("activationKey: $fakeKey"))

        assertEquals(fakeKey, hit?.activationKey)
        assertNull(hit?.macAddress)
        assertEquals(false, hit!!.isComplete)
    }

    @Test
    fun `ignores unrelated lines`() {
        val log = buildString {
            appendLine(line("write=00002760-08c2-11e1-9073-0e8ac72e0001"))
            appendLine("10-11 18:15:03.200  4821  4865 D BluetoothHelper: onCharacteristicWrite")
            appendLine("10-11 18:15:03.300  4821  4865 I ActivityManager: something else")
        }

        assertNull(LogcatParser.parse(log))
    }

    @Test
    fun `ignores a short hex blob that is not a key`() {
        // 只有 8 位 hex，不构成 32 位 activationKey
        assertNull(LogcatParser.parse(line("activationKey: DEADBEEF")))
    }

    @Test
    fun `parses the -v time prefix the grabber produces`() {
        // 抓取器用的是 `logcat -v time`，真机行首是 `D/TAG(PID): ` 而不是 threadtime 的 `D TAG: `。
        // 两种前缀都必须能解析 —— 下方时间戳取自 2026-10-11 真机实测。
        val raw = "10-11 18:43:38.551 20364 20364 D/VocLockHelperV2(20364): " +
            "unlock: macAddress: $fakeMac activationKey: $fakeKey"

        val hit = LogcatParser.parse(raw)

        assertEquals(fakeKey, hit?.activationKey)
        assertEquals(fakeMac, hit?.macAddress)
    }

    @Test
    fun `empty input yields null`() {
        assertNull(LogcatParser.parse(""))
    }
}
