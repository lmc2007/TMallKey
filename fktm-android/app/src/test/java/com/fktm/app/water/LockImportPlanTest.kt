package com.fktm.app.water

import com.fktm.app.credential.LockCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门锁自动入库的决策回归。
 *
 * 关键约定：**VOC 优先**。同一把锁两条 hook 都可能抓到 ——
 * 有静态钥匙（activationKey）就走离线，只有 syncData 的才登记成 MV。
 */
class LockImportPlanTest {

    private fun voc(mac: String, key: String) = ModuleEnvelope.DoorLock(mac, key)
    private fun mv(mac: String) = ModuleEnvelope.MvLock(mac, "")

    @Test
    fun `只有 VOC 钥匙时写入一把 offline 锁`() {
        val plan = LockImport.plan(emptyList(), listOf(voc("26:08:17:00:02:97", "KEY32")), emptyList())
        assertEquals(1, plan.credentials.size)
        val c = plan.credentials[0]
        assertEquals("26:08:17:00:02:97", c.address)
        assertEquals("KEY32", c.activationKey)
        assertEquals(LockCredential.TYPE_VOC, c.type)
        assertTrue(c.name.isNotBlank())
    }

    @Test
    fun `只有 MV 时登记成在线锁（无钥匙）`() {
        val plan = LockImport.plan(emptyList(), emptyList(), listOf(mv("D2017CA1D8A6")))
        assertEquals(1, plan.credentials.size)
        val c = plan.credentials[0]
        assertEquals("D2:01:7C:A1:D8:A6", c.address)
        assertEquals("", c.activationKey)
        assertEquals(LockCredential.TYPE_MV, c.type)
    }

    @Test
    fun `同一把锁同时出现时 VOC 优先`() {
        val mac = "26:08:17:00:02:97"
        val plan = LockImport.plan(emptyList(), listOf(voc(mac, "KEY")), listOf(mv(mac)))
        assertEquals(1, plan.credentials.size)
        assertEquals(LockCredential.TYPE_VOC, plan.credentials[0].type)
    }

    @Test
    fun `已有离线钥匙的锁不会被 MV 覆盖`() {
        val mac = "26:08:17:00:02:97"
        val existing = listOf(
            LockCredential(name = "宿舍门", address = mac, activationKey = "OLDKEY"),
        )
        val plan = LockImport.plan(existing, emptyList(), listOf(mv(mac)))
        assertTrue(plan.credentials.isEmpty())
    }

    @Test
    fun `已登记过的 MV 锁不重复写`() {
        val mac = "D2:01:7C:A1:D8:A6"
        val existing = listOf(
            LockCredential(name = "MV 锁 xx", address = mac, activationKey = "", type = LockCredential.TYPE_MV),
        )
        assertTrue(LockImport.plan(existing, emptyList(), listOf(mv(mac))).credentials.isEmpty())
    }

    @Test
    fun `钥匙更新时保留用户改过的名字`() {
        val mac = "26:08:17:00:02:97"
        val existing = listOf(LockCredential(name = "我的宿舍", address = mac, activationKey = "OLD"))
        val plan = LockImport.plan(existing, listOf(voc(mac, "NEW")), emptyList())
        assertEquals(1, plan.credentials.size)
        assertEquals("我的宿舍", plan.credentials[0].name)
        assertEquals("NEW", plan.credentials[0].activationKey)
    }

    @Test
    fun `钥匙没变就不写盘`() {
        val mac = "26:08:17:00:02:97"
        val existing = listOf(LockCredential(name = "宿舍门", address = mac, activationKey = "SAME"))
        assertTrue(LockImport.plan(existing, listOf(voc(mac, "SAME")), emptyList()).credentials.isEmpty())
    }

    @Test
    fun `空钥匙的条目被跳过`() {
        val plan = LockImport.plan(emptyList(), listOf(voc("26:08:17:00:02:97", "")), emptyList())
        assertTrue(plan.credentials.isEmpty())
    }
}
