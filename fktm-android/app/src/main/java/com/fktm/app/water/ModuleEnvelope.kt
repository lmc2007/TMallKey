package com.fktm.app.water

import org.json.JSONObject

/**
 * LSPatch 模块交给 fkTM 的「一包东西」。
 *
 * ## 两种格式都要认
 *
 * ```
 * 旧： {"cookie2":"…","unb":"…"}                                   ← 只有 cookie
 * 新： {"cookies":{…},"doorlocks":[{"mac","activationKey"}],"mvlocks":[{"mac","syncData"}]}
 * ```
 *
 * 旧格式必须继续支持：用户手机上的模块可能还是上一版，直接抛错会让整条导入链路静默失败。
 *
 * ## 门锁为什么跟着 cookie 一起来了
 *
 * VOC 门锁的 `activationKey` **只存在于门锁页的 H5 内存里**（服务端那个
 * `guide.door.lock.user.get` 在 h5 形态下一律回 `CE003`，见 PLAN-reverse 记录），
 * 所以只能在用户打开门锁页那一刻从 `VocLockHelper.unlock` 的 JSB 参数里截下来。
 * 模块因此把「cookie + 门锁 + MV 锁」打成整包推送，抓到就推，不用再等用户回 fkTM 拉。
 */
data class ModuleEnvelope(
    val cookies: Map<String, String>,
    val doorlocks: List<DoorLock>,
    val mvLocks: List<MvLock>,
) {

    /** 一把 VOC 锁：`activationKey` 是静态钥匙，拿到即可永久离线开锁。 */
    data class DoorLock(val mac: String, val activationKey: String)

    /**
     * 一把 MV 锁（掌门神通）。
     *
     * [syncData] 是服务端签发的动态开锁帧，**不能复用**；这里只用来判定「这把锁是 MV 型」。
     * 真正开锁时 fkTM 会自己用会话去 `zmst.unlock.data.get` 现取一帧。
     */
    data class MvLock(val mac: String, val syncData: String)

    companion object {

        /** 解析失败（不是 JSON）返回 null；其余情况尽量取到能用的部分。 */
        fun parse(raw: String): ModuleEnvelope? {
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null

            // 整包形态有 "cookies" 子对象；旧形态整个对象就是 cookie map
            val cookieObj = obj.optJSONObject("cookies") ?: obj
            val cookies = LinkedHashMap<String, String>()
            cookieObj.keys().forEach { k ->
                // 整包里的 "doorlocks"/"mvlocks" 是数组，optString 会拿到空串，天然被过滤掉
                val v = runCatching { cookieObj.optString(k) }.getOrNull().orEmpty()
                if (k.isNotBlank() && v.isNotBlank()) cookies[k] = v
            }
            if (cookies.isEmpty()) return null

            val doorlocks = obj.optJSONArray("doorlocks").mapObjects { o ->
                val mac = normalizeMac(o.optString("mac")) ?: return@mapObjects null
                val key = o.optString("activationKey").trim()
                if (key.isEmpty()) null else DoorLock(mac, key)
            }

            val mvLocks = obj.optJSONArray("mvlocks").mapObjects { o ->
                val mac = normalizeMac(o.optString("mac")) ?: return@mapObjects null
                MvLock(mac, o.optString("syncData").trim())
            }

            return ModuleEnvelope(cookies, doorlocks, mvLocks)
        }

        /**
         * MAC 统一成 `AA:BB:CC:DD:EE:FF`。
         *
         * 天猫校园的 H5 里两种写法都出现过（`260817000297` / `26:08:17:00:02:97`），
         * BLE 连接又必须用冒号形态，所以入口处就归一，避免后面每处都判一遍。
         * 12 位 hex 之外的一律原样返回（不认识的形态也别丢）。
         */
        fun normalizeMac(input: String?): String? {
            val s = input?.trim().orEmpty()
            if (s.isEmpty()) return null
            val hex = s.replace(Regex("[^0-9a-fA-F]"), "")
            if (hex.length != 12) return s
            return hex.chunked(2).joinToString(":").uppercase()
        }
    }
}

/** JSONArray 逐项解析的小工具：null 项直接跳过。 */
private inline fun <T> org.json.JSONArray?.mapObjects(block: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        block(o)?.let { out.add(it) }
    }
    return out
}
