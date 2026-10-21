package com.fktm.app.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 饮水模块的离线回归。
 *
 * 签名黄金向量来自 `fktm/water/mtop.py`（Python 侧同一套算法） ——
 * 两边算出来的 string 必须逐字节一致，否则 App 调接口会被网关判签名错。
 */
class WaterTest {

    /** 与 Python `json.dumps(..., separators=(",",":"), ensure_ascii=False)` 等价。 */
    @Test
    fun `紧凑 json 与 python 一致`() {
        val inner = WaterJson.stringify(
            linkedMapOf(
                "isv" to "CAMPUS",
                "businessType" to "WASH_AND_CARE",
                "resNo" to "tmxy869451084947543",
                "paymentChannel" to "TMXY_APP",
            )
        )
        assertEquals(
            """{"isv":"CAMPUS","businessType":"WASH_AND_CARE","resNo":"tmxy869451084947543","paymentChannel":"TMXY_APP"}""",
            inner,
        )

        val outer = WaterJson.stringify(
            linkedMapOf("requestType" to "DEVICE_INFO_GET", "requestJson" to inner)
        )
        assertEquals(
            """{"requestType":"DEVICE_INFO_GET","requestJson":"{\"isv\":\"CAMPUS\",\"businessType\":\"WASH_AND_CARE\",\"resNo\":\"tmxy869451084947543\",\"paymentChannel\":\"TMXY_APP\"}"}""",
            outer,
        )
    }

    /** 混合类型：中文不转义、整数不加小数点、布尔/null 用小写。 */
    @Test
    fun `混合类型序列化`() {
        val s = WaterJson.stringify(
            linkedMapOf(
                "campuses" to listOf("北京", "A"),
                "n" to 500960,
                "ok" to true,
                "nil" to null,
            )
        )
        assertEquals("""{"campuses":["北京","A"],"n":500960,"ok":true,"nil":null}""", s)
    }

    /** 签名黄金向量（由 Python 侧生成，改动算法会让这条挂）。 */
    @Test
    fun `签名黄金向量`() {
        val data =
            """{"requestType":"DEVICE_INFO_GET","requestJson":"{\"isv\":\"CAMPUS\",\"businessType\":\"WASH_AND_CARE\",\"resNo\":\"tmxy869451084947543\",\"paymentChannel\":\"TMXY_APP\"}"}"""
        assertEquals(
            "0d62dcbbe00e06239d258b72cd10158a",
            MtopClient.sign("8e1c9b7a5d2f4a6b", "1760000000000", "12574478", data),
        )
        val data2 = """{"campuses":["北京","A"],"n":500960,"ok":true,"nil":null}"""
        assertEquals(
            "822ea462ce875e002b0c1f512b68b7f9",
            MtopClient.sign("8e1c9b7a5d2f4a6b", "1760000000000", "12574478", data2),
        )
    }

    /** `_m_h5_tk` 形如 `<token>_<毫秒>`，只用前段。 */
    @Test
    fun `token 解析`() {
        assertEquals("abc123", MtopClient.parseToken("abc123_1760000000000"))
        assertEquals("", MtopClient.parseToken(null))
        assertEquals("", MtopClient.parseToken(""))
        assertEquals("abc123", MtopClient.parseToken("abc123"))
    }

    @Test
    fun `二维码解析`() {
        val cases = mapOf(
            "https://share.confong.cn/xh?id=tmxy869451084947543" to "tmxy869451084947543",
            "tmxy869451084947543" to "tmxy869451084947543",
            "630102040" to "630102040",
            "  https://share.confong.cn/xh?id=tmxy869451084947543&x=1  " to "tmxy869451084947543",
        )
        for ((raw, expect) in cases) {
            val d = QrCodec.parse(raw)
            assertEquals("解析 $raw", expect, d?.code)
            assertTrue(d!!.valid)
        }
        assertNull(QrCodec.parse("https://example.com/hello"))
        assertNull(QrCodec.parse(""))
    }

    /** 错误码翻人话：抓包里真实出现过的几个。 */
    @Test
    fun `错误码翻译`() {
        val err = MtopError(listOf("FAIL_SYS_SESSION_EXPIRED::登录态过期"))
        assertEquals("FAIL_SYS_SESSION_EXPIRED", err.code)
        assertTrue(err.retryable)
        assertEquals("登录态过期，需要刷新", err.human())

        // 淘宝把 EXPIRED 拼成 EXOIRED，别「修正」它
        assertTrue(MtopError(listOf("FAIL_SYS_TOKEN_EXOIRED::x")).retryable)
        assertEquals(false, MtopError(listOf("FAIL_SYS_UNAUTHORIZED_ENTRANCE::x")).retryable)
    }
}
