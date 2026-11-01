package com.fktm.app.water

import android.content.Context
import com.fktm.app.credential.CredentialStore
import com.fktm.app.credential.LockCredential

/**
 * 把模块抓到的门锁自动入库。
 *
 * 调用方：饮水页回来时的 [CookiePuller.pull]、模块的 Provider / 广播推送。
 * 全部走 [CredentialStore.upsert]（Keystore 加密落盘），同一把锁按 MAC 去重。
 *
 * ## 类型判定（VOC 优先）
 *
 * 同一把锁可能同时被两条 hook 抓到：
 *
 * · `VocLockHelper.unlock` → 拿到 **activationKey**（静态钥匙，离线可开）
 * · `ZMUnlockJsBridge.unlock` → 拿到 **syncData**（在线帧，只能证明「这是 MV 锁」）
 *
 * 两个都出现时按 VOC 处理 —— 有静态钥匙就没必要每次联网。
 * 只有 syncData 的才登记成 MV（开锁时现取帧）。
 */
object LockImport {

    data class Applied(val voc: Int = 0, val mv: Int = 0) {
        val any: Boolean get() = voc > 0 || mv > 0
        override fun toString(): String = "voc=$voc mv=$mv"
    }

    /** 纯决策结果（不碰 Android，便于单测）。 */
    data class Plan(val credentials: List<LockCredential>, val voc: Int, val mv: Int) {
        val any: Boolean get() = credentials.isNotEmpty()
    }

    /** 真正的写盘动作。 */
    fun apply(
        context: Context,
        doorlocks: List<ModuleEnvelope.DoorLock>,
        mvLocks: List<ModuleEnvelope.MvLock>,
    ): Applied {
        if (doorlocks.isEmpty() && mvLocks.isEmpty()) return Applied()
        val store = CredentialStore(context.applicationContext)
        val plan = plan(store.load(), doorlocks, mvLocks)
        plan.credentials.forEach { store.upsert(it) }
        return Applied(plan.voc, plan.mv)
    }

    /**
     * 决定要写哪些凭证（纯函数）。
     *
     * 规则：
     * 1. 有 `activationKey` 的 → VOC（覆盖同名旧值，钥匙可能换了）
     * 2. 只出现在 `mvlocks`、且本地没有钥匙的 → MV
     * 3. 已存在的名字保留，避免用户改过的名字被覆盖
     */
    fun plan(
        existing: List<LockCredential>,
        doorlocks: List<ModuleEnvelope.DoorLock>,
        mvLocks: List<ModuleEnvelope.MvLock>,
    ): Plan {
        val byAddress = existing.associateBy { it.address.uppercase() }
        val out = ArrayList<LockCredential>()
        val vocMacs = HashSet<String>()
        var voc = 0
        var mv = 0

        for (dl in doorlocks) {
            val mac = ModuleEnvelope.normalizeMac(dl.mac) ?: continue
            if (dl.activationKey.isBlank()) continue
            vocMacs.add(mac.uppercase())
            val old = byAddress[mac.uppercase()]
            if (old != null && old.activationKey == dl.activationKey && !old.isMv) continue
            out.add(
                LockCredential(
                    name = old?.name ?: "门锁 ${mac.takeLast(5)}",
                    address = mac,
                    activationKey = dl.activationKey,
                    type = LockCredential.TYPE_VOC,
                )
            )
            voc++
        }

        for (m in mvLocks) {
            val mac = ModuleEnvelope.normalizeMac(m.mac) ?: continue
            if (vocMacs.contains(mac.uppercase())) continue
            val old = byAddress[mac.uppercase()]
            if (old != null && old.activationKey.isNotBlank()) continue   // 已有离线钥匙
            if (old != null && old.isMv) continue                        // 已登记过
            out.add(
                LockCredential(
                    name = old?.name ?: "MV 锁 ${mac.takeLast(5)}",
                    address = mac,
                    activationKey = "",
                    type = LockCredential.TYPE_MV,
                )
            )
            mv++
        }

        return Plan(out, voc, mv)
    }
}
