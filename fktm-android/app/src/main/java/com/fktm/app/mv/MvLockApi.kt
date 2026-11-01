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

    /**
     * 拉一次开锁数据。每次调用签发的 encryptData 都不同，取到就立刻用。
     *
     * `macId` 的写法服务端两种都见过（`D2017CA1D8A6` / `D2:01:7C:A1:D8:A6`），
     * 传错了只回一句 `FAIL_BIZ_CE003::参数异常`，看起来像"锁没绑"其实只是格式不对。
     * 所以这里按「原样 → 冒号 → 无分隔」依次试，**只在参数类错误时才换格式**
     * （锁未绑定之类的业务错误不重试，避免白等三次网络）。
     */
    fun fetchUnlockData(macId: String): UnlockData {
        var lastError: Exception? = null
        for (candidate in macCandidates(macId)) {
            try {
                return fetchOnce(candidate)
            } catch (e: MvApiException) {
                lastError = e
                if (!e.maybeParamIssue) throw e       // 业务错误：换格式也没用
            }
        }
        throw lastError ?: IllegalStateException("没有可用的 macId")
    }

    private fun fetchOnce(macId: String): UnlockData {
        val raw = client.call(
            "mtop.tmall.campus.guide.zmst.unlock.data.get",
            linkedMapOf<String, Any?>("macId" to macId),
        )
        val body = JSONObject(raw)
        val ret = body.optJSONArray("ret")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        if (ret.any { !it.startsWith("SUCCESS") }) {
            throw MvApiException(ret.joinToString(" | "), macId)
        }
        val data = body.optJSONObject("data")
            ?: throw IllegalStateException("响应缺少 data 字段")
        val enc = data.optString("encryptData").trim()
        if (enc.isEmpty()) {
            throw MvApiException("服务端未返回 encryptData（${data.optString("errorMsg")}）", macId)
        }
        return UnlockData(
            macId = data.optString("macId", macId).ifBlank { macId },
            deviceName = data.optString("deviceName"),
            encryptData = enc,
        )
    }

    /** 服务端报错（ret 非 SUCCESS）。 */
    class MvApiException(message: String, val macId: String) : Exception(message) {
        /** 参数形态类错误：换一种 macId 写法还有救。 */
        val maybeParamIssue: Boolean
            get() = listOf("CE003", "参数", "BIZPARAM", "PROTOPARAM").any {
                message?.contains(it) == true
            }
    }

    private fun macCandidates(raw: String): List<String> {
        val out = LinkedHashSet<String>()
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty()) out.add(trimmed)
        val hex = trimmed.replace(Regex("[^0-9a-fA-F]"), "")
        if (hex.length == 12) {
            out.add(hex.chunked(2).joinToString(":").uppercase())
            out.add(hex.uppercase())
        }
        return out.toList()
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
