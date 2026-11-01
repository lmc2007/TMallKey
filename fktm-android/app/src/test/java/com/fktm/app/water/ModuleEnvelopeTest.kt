package com.fktm.app.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块整包解析的回归。
 *
 * 两种格式必须同时认：
 * ```
 * 旧：{"cookie2":"…","unb":"…"}
 * 新：{"cookies":{…},"doorlocks":[…],"mvlocks":[…]}
 * ```
 * 用户手机上的模块版本不可控，旧格式一旦解析失败，整条免 root 导入会静默失效。
 */
class ModuleEnvelopeTest {

    @Test
    fun `旧格式（纯 cookie map）仍然可解析`() {
        val env = ModuleEnvelope.parse("""{"cookie2":"cm","unb":"2209882040940"}""")
        assertEquals("cm", env?.cookies?.get("cookie2"))
        assertEquals("2209882040940", env?.cookies?.get("unb"))
        assertTrue(env!!.doorlocks.isEmpty())
        assertTrue(env.mvLocks.isEmpty())
    }

    @Test
    fun `新格式同时带 cookie 与门锁`() {
        val raw = """
            {
              "cookies": {"cookie2":"cm","unb":"u1"},
              "doorlocks": [{"mac":"26:08:17:00:02:97","activationKey":"DEADBEEF"}],
              "mvlocks": [{"mac":"D2:01:7C:A1:D8:A6","syncData":"4C001a01"}]
            }
        """.trimIndent()
        val env = ModuleEnvelope.parse(raw)!!
        assertEquals("cm", env.cookies["cookie2"])
        assertEquals(1, env.doorlocks.size)
        assertEquals("26:08:17:00:02:97", env.doorlocks[0].mac)
        assertEquals("DEADBEEF", env.doorlocks[0].activationKey)
        assertEquals(1, env.mvLocks.size)
        assertEquals("D2:01:7C:A1:D8:A6", env.mvLocks[0].mac)
    }

    @Test
    fun `整包里的 doorlocks 不能混进 cookie jar`() {
        val raw = """{"cookies":{"cookie2":"cm"},"doorlocks":[{"mac":"26:08:17:00:02:97","activationKey":"K"}]}"""
        val env = ModuleEnvelope.parse(raw)!!
        // 数组被 optString 取成空串后被过滤，不会出现 doorlocks 这个键
        assertTrue(env.cookies.keys.none { it == "doorlocks" })
        assertEquals(1, env.cookies.size)
    }

    @Test
    fun `MAC 统一成冒号大写`() {
        assertEquals("26:08:17:00:02:97", ModuleEnvelope.normalizeMac("260817000297"))
        assertEquals("26:08:17:00:02:97", ModuleEnvelope.normalizeMac("26-08-17-00-02-97"))
        assertEquals("26:08:17:00:02:97", ModuleEnvelope.normalizeMac("26:08:17:00:02:97"))
        assertNull(ModuleEnvelope.normalizeMac("  "))
        // 不认识的形态原样保留，不能把锁丢了
        assertEquals("weird-lock", ModuleEnvelope.normalizeMac("weird-lock"))
    }

    @Test
    fun `空钥匙的 VOC 条目被丢弃`() {
        val raw = """{"cookies":{"cookie2":"cm"},"doorlocks":[{"mac":"26:08:17:00:02:97","activationKey":""}]}"""
        assertTrue(ModuleEnvelope.parse(raw)!!.doorlocks.isEmpty())
    }

    @Test
    fun `没有 cookie 的包直接判无效`() {
        assertNull(ModuleEnvelope.parse("""{"cookies":{},"doorlocks":[]}"""))
        assertNull(ModuleEnvelope.parse("not json"))
        assertNull(ModuleEnvelope.parse("null"))
    }

    @Test
    fun `MV 锁没有 syncData 也要保留 MAC`() {
        val raw = """{"cookies":{"cookie2":"cm"},"mvlocks":[{"mac":"D2017CA1D8A6"}]}"""
        val env = ModuleEnvelope.parse(raw)!!
        assertEquals("D2:01:7C:A1:D8:A6", env.mvLocks[0].mac)
        assertEquals("", env.mvLocks[0].syncData)
    }
}
