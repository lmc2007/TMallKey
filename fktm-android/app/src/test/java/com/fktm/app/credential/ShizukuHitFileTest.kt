package com.fktm.app.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ShizukuLogcatGrabber.pickHit] 的用例。
 *
 * 它对应的是「服务端写下的结果文件长什么样、还算不算数」这一个判断 ——
 * 也就是把抓取结果从「binder 回复」换成「文件」之后新增的全部逻辑。
 * 文件内容刻意就是 logcat 的**原始那一行**，所以这里也能顺带验证两处格式一致。
 *
 * 行里的 MAC 与 activationKey 都是**假值**：真钥匙不入库。
 */
class ShizukuHitFileTest {

    private val now = 1_700_000_000_000L

    private val fakeMac = "AA:BB:CC:DD:EE:FF"
    private val fakeKey = "DEADBEEFCAFEBABE0123456789ABCDEF"

    private val sampleLine =
        "10-11 18:43:38.551 20364 20364 D/VocLockHelperV2: " +
            "unlock: macAddress: $fakeMac activationKey: ${fakeKey.lowercase()}"

    @Test
    fun `fresh hit file is accepted`() {
        val hit = ShizukuLogcatGrabber.pickHit(sampleLine, now - 1_000, now, 60_000)
        assertEquals(fakeMac, hit?.macAddress)
        assertEquals(fakeKey, hit?.activationKey)
    }

    @Test
    fun `stale hit file is rejected`() {
        // 文件太旧 → 多半是上一次抓取的残留，不能当成这次的结果
        assertNull(ShizukuLogcatGrabber.pickHit(sampleLine, now - 60_001, now, 60_000))
    }

    @Test
    fun `hit exactly at the age limit is accepted`() {
        // 边界：刚好等于上界仍然有效（判断用的是严格大于）
        assertEquals(
            fakeKey,
            ShizukuLogcatGrabber.pickHit(sampleLine, now - 60_000, now, 60_000)?.activationKey,
        )
    }

    @Test
    fun `missing timestamp does not reject the hit`() {
        // mtime 读不到（0）时不应该把结果丢掉
        assertEquals(
            fakeKey,
            ShizukuLogcatGrabber.pickHit(sampleLine, 0L, now, 60_000)?.activationKey,
        )
    }

    @Test
    fun `null or blank content yields null`() {
        assertNull(ShizukuLogcatGrabber.pickHit(null, now, now, 60_000))
        assertNull(ShizukuLogcatGrabber.pickHit("", now, now, 60_000))
        assertNull(ShizukuLogcatGrabber.pickHit("   \n  ", now, now, 60_000))
    }

    @Test
    fun `content without an activationKey yields null`() {
        assertNull(
            ShizukuLogcatGrabber.pickHit(
                "unlock: macAddress: $fakeMac",
                now,
                now,
                60_000,
            )
        )
    }
}
