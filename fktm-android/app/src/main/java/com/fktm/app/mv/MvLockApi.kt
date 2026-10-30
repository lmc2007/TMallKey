package com.fktm.app.mv

import com.fktm.app.water.MtopClient
import org.json.JSONObject

/**
 * MV 锁（掌门神通 ZMST）开锁数据接口 —— 测试版。
 *
 * 协议逆向结论（2026-10-30，p_doorlock-index.js + PC 实测）：
 *
 * ```
 * mtop.tmall.campus.guide.zmst.unlock.data.get { macId }
 *     → { macId, deviceName, encryptData }
 * ```
 *
 * · `encryptData` 就是 BLE 开锁帧（一次一密，帧头 4C001a0106011206 固定）
 * · 实测服务端**不校验锁归属**（任意登录会话可查任意锁）
 * · 不走 share.applet 资金域，CM 层校验情况未定（CM❌ 会话待测）
 *
 * 会话来源 = [com.fktm.app.water.WaterSession]（root 导入的官方版登录态）。
 */
class MvLockApi(cookies: Map<String, String>) {

    private val client = MtopClient(LinkedHashMap(cookies))

    /** `zmst.unlock.data.get` 的返回。 */
    data class UnlockData(
        val macId: String,
        val deviceName: String,
        /** BLE 开锁帧（hex），原样写入 AFF1 即可。 */
        val encryptData: String,
    )

    /** 拉一次开锁数据。每次调用签发的 encryptData 都不同，取到就立刻用。 */
    fun fetchUnlockData(macId: String): UnlockData {
        val body = client.call(
            "mtop.tmall.campus.guide.zmst.unlock.data.get",
            linkedMapOf<String, Any?>("macId" to macId),
        )
        val data = JSONObject(body).optJSONObject("data")
            ?: throw IllegalStateException("响应缺少 data 字段")
        val enc = data.optString("encryptData").trim()
        require(enc.isNotEmpty()) { "服务端未返回 encryptData（错误：${data.opt("errorMsg") ?: "未知"}）" }
        return UnlockData(
            macId = data.optString("macId", macId),
            deviceName = data.optString("deviceName"),
            encryptData = enc,
        )
    }

    companion object {
        /** 容忍各种写法：大小写、冒号/横杠/无分隔。非法返回 null。 */
        fun normalizeMac(input: String): String? {
            val hex = input.replace(Regex("[^0-9a-fA-F]"), "")
            if (hex.length != 12) return null
            return hex.chunked(2).joinToString(":").uppercase()
        }
    }
}
