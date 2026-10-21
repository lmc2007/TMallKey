package com.fktm.app.water

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 校园饮水的 mtop 网关客户端（饮水模块地基）。
 *
 * 饮水业务全在合作方的 H5 里，控制接口走的是淘宝标准 mtop 网关。这一层只做三件事：
 *  · 算签名（纯函数，可离线单测）
 *  · 管 token（`_m_h5_tk` 的两次握手）
 *  · 发请求 / 归一化错误
 *
 * 不含业务参数 —— 业务在 [WaterApi]，登录态在 [WaterSession]。
 *
 * ## 一次调用长什么样（与 `fktm/water/mtop.py` 逐字一致）
 *
 * ```
 * GET https://h5api.m.taobao.com/h5/{api}/{ver}/
 *     ?jsv=2.7.2 & appKey=12574478 & t=<ms> & sign=<md5>
 *     & api= & v=1.0 & type=originaljson & dataType=json & data=<json>
 * sign = md5(token & t & appKey & data)      token = _m_h5_tk 的 "_" 前段
 * ```
 *
 * ⚠ 签名用的 data 必须和真正发出去的 data **是同一个字符串**，
 *   所以 JSON 由本文件的 [WaterJson] 自己序列化（紧凑、无空格、中文不转义），
 *   不交给第三方库——不同实现的空格/转义策略会让签名对不上。
 *
 * ## 首调必然失败一次，这是设计而不是 bug
 *
 * 没有 `_m_h5_tk` 时第一次请求返回 `FAIL_SYS_TOKEN_EMPTY`，但响应会
 * `Set-Cookie` 塞下来新 token，用它重试即可。[call] 内建了这次握手。
 */
class MtopError(
    val ret: List<String>,
    val payload: String = "",
) : Exception(ret.firstOrNull() ?: "UNKNOWN") {

    /** 冒号前的错误码，如 `FAIL_SYS_TOKEN_EMPTY`。 */
    val code: String = ret.firstOrNull()?.substringBefore("::") ?: "UNKNOWN"

    /** 冒号后的详情。 */
    val detail: String = ret.firstOrNull()?.substringAfter("::", "") ?: ""

    /** 只有 token 类错误值得重试：服务端刚塞下来的新 token 还没用上。 */
    val retryable: Boolean get() = code in MtopClient.RETRY_CODES

    fun human(): String = MtopClient.CODES[code] ?: detail.ifBlank { code }
}

/** 紧凑 JSON 序列化 —— 签名与发送共用同一份输出。 */
object WaterJson {

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> sb.append(if (v == Math.rint(v) && !v.isInfinite()) v.toLong().toString() else v.toString())
            is Float -> write(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, item) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    write(sb, item)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.toList())
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}

class MtopClient(
    /** 只保存 mtop 必需的几项（cookie2 / _tb_token_ / _m_h5_tk …），不存密码。 */
    private val cookies: MutableMap<String, String> = linkedMapOf(),
    private val appKey: String = APP_KEY,
    private val timeoutMs: Int = 15_000,
) {

    /** 当前 token（`_m_h5_tk` 的 `_` 前段）。没有就返回空串，触发首次握手。 */
    fun token(): String = parseToken(cookies[TOKEN_COOKIE])

    fun cookieCount(): Int = cookies.size

    /** 导出当前 cookie（登录成功后交给 [WaterSession] 保存）。 */
    fun export(): Map<String, String> = LinkedHashMap(cookies)

    /** 合并外部拿到的 cookie（例如人机验证页面种下的 `x5sec`）。 */
    fun merge(extra: Map<String, String>) {
        extra.forEach { (k, v) -> cookies[k] = v }
    }

    /**
     * 调一个 mtop 接口，成功返回完整响应 JSON。
     *
     * @param data 平铺参数（饮水链路绝大多数是这种）
     * @param maxRetry token 类错误的重试次数
     * @throws MtopError 网关 ret 不是 SUCCESS，或网络层失败
     */
    fun call(
        api: String,
        data: Map<String, Any?> = emptyMap(),
        version: String = "1.0",
        maxRetry: Int = 2,
    ): String {
        var last: MtopError? = null
        for (attempt in 0..maxRetry) {
            val payload = WaterJson.stringify(data)
            val t = System.currentTimeMillis().toString()
            val url = buildUrl(api, version, t, payload)
            val (body, setCookies) = httpGet(url)
            absorb(setCookies)

            val ret = parseRet(body)
            if (ret.isNotEmpty() && ret[0].startsWith("SUCCESS")) return body

            val err = MtopError(ret, body)
            last = err
            if (attempt < maxRetry && err.retryable) continue
            throw err
        }
        throw last ?: MtopError(listOf("FAIL_SYS_UNKNOWN::未知失败"))
    }

    private fun buildUrl(api: String, version: String, t: String, payload: String): String {
        val q = listOf(
            "jsv" to "2.7.2",
            "appKey" to appKey,
            "t" to t,
            "sign" to sign(token(), t, appKey, payload),
            "api" to api,
            "v" to version,
            "type" to "originaljson",
            "dataType" to "json",
            "data" to payload,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return "${GATEWAY_PREFIX}$api/$version/?$q"
    }

    private fun httpGet(url: String): Pair<String, List<String>> {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://share.confong.cn/")
                setRequestProperty("Accept", "application/json")
                if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookieHeader())
            }
        } catch (e: Throwable) {
            throw MtopError(listOf("NETWORK_ERROR::${e.message ?: e.javaClass.simpleName}"))
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val setCookies = conn.headerFields["Set-Cookie"] ?: emptyList()
            if (body.isBlank()) throw MtopError(listOf("NETWORK_ERROR::HTTP $code 空响应"))
            body to setCookies
        } catch (e: MtopError) {
            throw e
        } catch (e: Throwable) {
            throw MtopError(listOf("NETWORK_ERROR::${e.message ?: e.javaClass.simpleName}"))
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 吸收响应里的 Set-Cookie（只取 `name=value` 前段）。 */
    private fun absorb(setCookies: List<String>) {
        for (raw in setCookies) {
            val first = raw.substringBefore(';')
            val name = first.substringBefore('=', "").trim()
            if (name.isEmpty() || !first.contains('=')) continue
            cookies[name] = first.substringAfter('=', "")
        }
    }

    private fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    companion object {
        const val APP_KEY = "12574478"
        private const val GATEWAY_PREFIX = "https://h5api.m.taobao.com/h5/"
        private const val TOKEN_COOKIE = "_m_h5_tk"
        /** 请求 UA。WebView 也用它 —— UA 不一致会被风控当成「指纹与请求不同源」。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"

        /** 需要重新握手的错误（token 空 / 过期 / 被踢）。 */
        val RETRY_CODES = setOf(
            "FAIL_SYS_TOKEN_EMPTY",
            "FAIL_SYS_TOKEN_EXOIRED",   // 淘宝拼写就是 EXOIRED，别「修正」它
            "FAIL_SYS_TOKEN_EXPIRED",
            "FAIL_SYS_SESSION_EXPIRED",
            "FAIL_SYS_ILLEGAL_ACCESS",
        )

        /** 业务码 → 人话。都是抓包/探针里真实出现过的。 */
        val CODES: Map<String, String> = mapOf(
            "1048" to "订单未找到（还没下单，正常）",
            "1018" to "业务类型不合法（businessType 传错）",
            "1077" to "ISV 不合法（isv 不是 CAMPUS）",
            "1106" to "缺 resNo（二维码没解析出设备号）",
            "UNKNOWN_DEVICE" to "未知的设备（该编号不在本校区）",
            "999" to "服务开小差了（风控或参数缺）",
            "CAMPUS_AREA_INFO_ID_NULL" to "缺 campusAreaInfoId",
            "FAIL_SYS_BIZPARAM_MISSED" to "缺业务参数（入参风格可能传错）",
            "FAIL_SYS_UNAUTHORIZED_ENTRANCE" to "接口未授权",
            "FAIL_SYS_TOKEN_EMPTY" to "令牌为空（首次握手，重试即可）",
            "FAIL_SYS_SESSION_EXPIRED" to "登录态过期，需要刷新",
        )

        /** `md5(token & t & appKey & data)`，小写 hex。空值就是空串，别塞 "null"。 */
        fun sign(token: String, t: String, appKey: String, data: String): String {
            val raw = "$token&$t&$appKey&$data"
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) sb.append(String.format("%02x", b))
            return sb.toString()
        }

        /**
         * `_m_h5_tk` 形如 `<token>_<毫秒时间戳>`，签名只用前半段。
         *
         * ⚠ 没有 `_` 时返回**整串**（Python 的 `split("_",1)[0]` 语义），
         *   不能传 `substringBefore('_', "")` —— 那是「分隔符缺失就返回空」。
         */
        fun parseToken(cookieValue: String?): String =
            cookieValue?.substringBefore('_')?.trim() ?: ""

        /** 从响应体里捞 `ret` 数组。解析不出来就当空（会走 MtopError 分支）。 */
        fun parseRet(body: String): List<String> = runCatching {
            val arr = org.json.JSONObject(body).optJSONArray("ret") ?: return emptyList()
            (0 until arr.length()).map { arr.optString(it) }
        }.getOrDefault(emptyList())
    }
}
