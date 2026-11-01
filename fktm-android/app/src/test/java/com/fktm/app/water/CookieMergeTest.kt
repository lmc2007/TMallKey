package com.fktm.app.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多域 cookie 合并的回归。
 *
 * 背景（2026-11-01 实测）：同一个账号在 WebView 里有两条 `cookie2`——
 * `.taobao.com`（淘宝登录态）和 `.confong.cn`（校园侧会话）。
 * CM 身份**只认 confong 那条**；一旦被 taobao 覆盖，`member.app.user.get`
 * 就回「用户未登录」，看上去像「补丁版没有 CM」，实际是导入丢 cookie。
 */
class CookieMergeTest {

    @Test
    fun `confong 的 cookie2 覆盖 taobao 的同名值`() {
        val jar = mergeCookieRows(
            listOf(
                CookieRow(".confong.cn", "cookie2", "cm-value"),
                CookieRow(".confong.cn", "munb", "2223007013759"),
                CookieRow(".taobao.com", "cookie2", "tb-value"),
                CookieRow(".taobao.com", "unb", "2209882040940"),
            )
        )
        assertEquals("cm-value", jar["cookie2"])
        // 其它域不冲突的键照常保留
        assertEquals("2209882040940", jar["unb"])
        assertEquals("2223007013759", jar["munb"])
    }

    @Test
    fun `confong 行在前在后都成立`() {
        val taobaoFirst = mergeCookieRows(
            listOf(CookieRow(".taobao.com", "cookie2", "tb"), CookieRow(".confong.cn", "cookie2", "cm"))
        )
        val confongFirst = mergeCookieRows(
            listOf(CookieRow(".confong.cn", "cookie2", "cm"), CookieRow(".taobao.com", "cookie2", "tb"))
        )
        assertEquals("cm", taobaoFirst["cookie2"])
        assertEquals("cm", confongFirst["cookie2"])
    }

    @Test
    fun `只有 taobao 时保持原样`() {
        val jar = mergeCookieRows(listOf(CookieRow(".taobao.com", "cookie2", "tb")))
        assertEquals("tb", jar["cookie2"])
    }

    @Test
    fun `空值行被丢弃且不覆盖已有值`() {
        val jar = mergeCookieRows(
            listOf(
                CookieRow(".confong.cn", "cookie2", "cm"),
                CookieRow(".taobao.com", "cookie2", ""),
            )
        )
        assertEquals("cm", jar["cookie2"])
        assertTrue(jar.isNotEmpty())
    }

    @Test
    fun `Set-Cookie 里 taobao 域的 cookie2 不能顶掉 CM 会话`() {
        val jar = linkedMapOf("cookie2" to "cm-value", "_m_h5_tk" to "old")
        val c = MtopClient(jar)
        c.absorb(
            listOf(
                "cookie2=tb-value;Path=/;Domain=.taobao.com;Max-Age=-1;HttpOnly",
                "_m_h5_tk=new-token_123;Path=/;Domain=taobao.com;Max-Age=5400",
            )
        )
        // CM 会话没被顶掉，其它 cookie 正常吸收
        assertEquals("cm-value", jar["cookie2"])
        assertEquals("new-token_123", jar["_m_h5_tk"])
    }

    @Test
    fun `confong 域下发的 cookie2 可以覆盖（会话续期）`() {
        val jar = linkedMapOf("cookie2" to "old-cm")
        val c = MtopClient(jar)
        c.absorb(listOf("cookie2=new-cm;Path=/;Domain=.confong.cn;Max-Age=-1;HttpOnly"))
        assertEquals("new-cm", jar["cookie2"])
    }

    @Test
    fun `空 jar 时允许写入 cookie2（登录首包）`() {
        val jar = linkedMapOf<String, String>()
        val c = MtopClient(jar)
        c.absorb(listOf("cookie2=tb-first;Path=/;Domain=.taobao.com"))
        assertEquals("tb-first", jar["cookie2"])
    }
}
