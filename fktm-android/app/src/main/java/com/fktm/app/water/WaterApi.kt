package com.fktm.app.water

import org.json.JSONObject

/** 一张饮水机二维码解析出来的结果。 */
data class ScannedDevice(
    val raw: String,
    /** 二维码里的编号，形如 `tmxy869451084947543`；没有前缀时就是裸数字。 */
    val code: String,
) {
    val valid: Boolean get() = code.isNotBlank()
}

/** 设备详情（来自 `general.device.info`）。 */
data class WaterDevice(
    /** 查询用的编号（二维码那个）。 */
    val query: String,
    /** ★ 真正的水机资源号 `resNo`，`water.start` 要的就是它。 */
    val resNo: String,
    /** 设备 ID（H5 里叫 `deviceId`，与 [resNo] 完全不同）。 */
    val deviceId: String,
    val name: String,
    val address: String,
    val building: String,
    val campus: String,
    val online: Boolean,
    val canUse: Boolean,
    val inBusinessTime: Boolean,
    val runningStatus: String,
    val faultText: String,
) {
    /** 能不能点「解锁」。 */
    val unlockable: Boolean get() = canUse && online && inBusinessTime && faultText.isBlank()
}

/** 解锁结果（`device.water.start` 的响应）。 */
data class StartResult(
    val isvOrderId: String,
    val serviceInfoId: String,
    val raw: String,
)

/**
 * 校园饮水业务接口。
 *
 * ## 命名陷阱（实测对照，别想当然）
 *
 * ```
 * 二维码            tmxy869451084947543     ← 带 tmxy 前缀
 * 真设备 ID          869451084947543         ← 去掉前缀
 * resNo            630102040               ← 资源号，与设备 ID 无算术关系
 * water.start 的 deviceId   630102040       ← 是 resNo，不是设备 ID！
 * ```
 *
 * 好在 `general.device.info` 的 `resNo` 字段**同时接受两种写法**
 * （`tmxy...` 和 `630102040`），返回里带着 `deviceCode`（= resNo）与 `deviceId`，
 * 所以扫码 → 解锁只需要两次调用：
 *
 * ```
 * 扫码 tmxy869451084947543
 *   → general.device.info   {resNo: "tmxy869451084947543"}   → deviceCode=630102040
 *   → device.water.start    {deviceId: "630102040"}          → 设备开锁
 * ```
 *
 * ⚠ 解锁**不等于出水**：服务端只是把机器开锁，出水要按机器上的物理按键；
 *   不按就超时按 0 元自动结单（本校接水免费）。
 */
class WaterApi(private val client: MtopClient) {

    /** 设备详情。`query` 可以是二维码原文，也可以直接是 `630102040` 这种资源号。 */
    fun deviceInfo(query: String): WaterDevice {
        val inner = WaterJson.stringify(
            mapOf(
                "isv" to ISV,
                "businessType" to BUSINESS_TYPE,
                "resNo" to query,
                "paymentChannel" to PAYMENT_CHANNEL,
            )
        )
        // ⚠ device.info 是**包装式**（少数老接口才这样），扁平会返回 code=999
        val body = client.call(
            API_DEVICE_INFO,
            mapOf("requestType" to "DEVICE_INFO_GET", "requestJson" to inner),
        )
        val data = JSONObject(body).optJSONObject("data")?.optJSONObject("data")
            ?: throw MtopError(listOf("EMPTY::设备信息为空"))
        val d = data.optJSONObject("deviceResponse")
            ?: throw MtopError(listOf("EMPTY::设备信息结构变了（没有 deviceResponse）"))
        val biz = data.optJSONObject("bizTimeConfig")

        return WaterDevice(
            query = query,
            resNo = d.optString("deviceCode").ifBlank { query },
            deviceId = d.optString("deviceId"),
            name = d.optString("deviceName").ifBlank { "饮水机" },
            address = d.optString("address"),
            building = d.optString("buildingName"),
            campus = d.optString("campusAreaName"),
            online = d.optString("onlineStatus").uppercase() != "OFFLINE",
            canUse = d.optBoolean("deviceCanUse", true),
            inBusinessTime = biz?.optBoolean("isInBusinessTime", true) ?: true,
            runningStatus = d.optString("runningStatus"),
            faultText = d.optString("faultInfo").ifBlank {
                if (d.optString("faultStatus") == "WELL_FUNCTIONING") "" else d.optString("errorMsg")
            },
        )
    }

    /**
     * 换校园用户（`mtop.tmall.campus.member.user.login`，**无入参**）。
     *
     * 这是校园身份的真正闸门：它只认「被天猫校园授权过的会话」，
     * 普通淘宝会话会回 `GET_CAMPUS_USER_ERROR`。
     *
     * @return null 表示通过，否则返回错误描述
     */
    fun confirmCampusUser(): String? = try {
        client.call(API_CAMPUS_LOGIN, emptyMap())
        null
    } catch (e: MtopError) {
        "${e.code}：${e.human()}"
    }

    /**
     * 登录态自检：拿一台已知设备去查详情。
     *
     * ⚠ `member.user.login` 对**任何**登录态都返回 OK，不能当校园身份闸门（实测证实）。
     *   真正会区分的是 `general.device.info` —— 会话没有校园档案时它回
     *   `GET_CAMPUS_USER_ERROR`。所以拿本校一台已知机器的资源号来探，只读。
     *
     * @return null 表示通过，否则返回错误描述
     */
    fun selfCheck(): String? = try {
        deviceInfo(PROBE_RES_NO)
        null
    } catch (e: MtopError) {
        "${e.code}：${e.human()}"
    } catch (e: Throwable) {
        e.message ?: e.javaClass.simpleName
    }

    /** 校园身份探测结果。 */
    data class CampusProbe(val ok: Boolean, val code: String, val message: String)

    /**
     * 探一下这个登录态在天猫校园侧有没有「校园身份」。
     *
     * `businessTime` 是最轻的校园侧接口（只要楼栋号），三种结果各不相同：
     *  · `FAIL_SYS_SESSION_EXPIRED` → 没登录 / 会话无效；
     *  · `GET_CAMPUS_USER_ERROR`    → 登录了，但取不到校园用户档案；
     *  · SUCCESS                    → 有校园身份，可以接水。
     *
     * ⚠ `GET_CAMPUS_USER_ERROR` 的文案是「请重新尝试」，所以这里**重试两次**：
     *   它可能是服务端瞬时故障，也可能真的是账号没绑定 —— 用重试把两者区分开。
     */
    fun probeCampusUser(): CampusProbe {
        var last: CampusProbe? = null
        repeat(2) { attempt ->
            try {
                client.call(
                    API_BUSINESS_TIME,
                    mapOf(
                        "relationshipId" to RELATIONSHIP_ID,
                        "buildingId" to BUILDING_ID,
                        "washDeviceType" to DEVICE_DRINKING,
                    ),
                )
                return CampusProbe(true, "SUCCESS", "校园身份正常")
            } catch (e: MtopError) {
                last = when (e.code) {
                    "GET_CAMPUS_USER_ERROR" -> CampusProbe(
                        false,
                        e.code,
                        "这个淘宝账号在天猫校园里没有校园身份（${e.detail}）。\n" +
                            "请确认登录的是你平时用天猫校园的那个淘宝账号。",
                    )
                    "FAIL_SYS_SESSION_EXPIRED" -> CampusProbe(false, e.code, "登录态无效，请重新登录")
                    else -> CampusProbe(false, e.code, e.human())
                }
                // 只对「看起来是服务端抽风」的那个码重试；其它错误重试没意义
                if (e.code != "GET_CAMPUS_USER_ERROR" || attempt == 1) return last!!
            }
        }
        return last ?: CampusProbe(false, "UNKNOWN", "探测失败")
    }

    /**
     * 解锁设备（服务端建单）。
     *
     * 实测原始报文：`{"deviceId":"630102040","paymentChannel":"TMXY_APP",
     * "deviceConsumeSourceStr":"FAST_START"}` —— 扁平参数，`deviceId` 填 **resNo**。
     */
    fun start(resNo: String, source: String = "FAST_START"): StartResult {
        val body = client.call(
            API_WATER_START,
            mapOf(
                "deviceId" to resNo,
                "paymentChannel" to PAYMENT_CHANNEL,
                "deviceConsumeSourceStr" to source,
            ),
        )
        val data = JSONObject(body).optJSONObject("data")
        return StartResult(
            isvOrderId = data?.optString("isvOrderId").orEmpty(),
            serviceInfoId = data?.optString("shareServiceInfoId").orEmpty(),
            raw = body,
        )
    }

    /**
     * 结束接水并结算。
     *
     * ⚠ 未在真机验证过（本轮解锁后没人按键，超时 0 元自动结单）。
     * 按同族接口的扁平风格拼，字段以实际返回为准。
     */
    fun stop(resNo: String, isvOrderId: String = ""): String {
        val data = mutableMapOf<String, Any?>(
            "deviceId" to resNo,
            "paymentChannel" to PAYMENT_CHANNEL,
        )
        if (isvOrderId.isNotBlank()) data["isvOrderId"] = isvOrderId
        return client.call(API_WATER_STOP, data)
    }

    companion object {
        const val ISV = "CAMPUS"                    // 盲试 1/2/3/TMXY 全错，真值是这个
        const val BUSINESS_TYPE = "WASH_AND_CARE"   // 洗护与饮水共用
        const val PAYMENT_CHANNEL = "TMXY_APP"
        const val DEVICE_DRINKING = "DRINKING_MACHINE"

        const val API_DEVICE_INFO = "mtop.tmall.campus.share.applet.general.device.info"
        const val API_WATER_START = "mtop.tmall.campus.share.applet.device.water.start"
        const val API_WATER_STOP = "mtop.tmall.campus.share.applet.device.water.stop"
        const val API_BUSINESS_TIME = "mtop.tmall.campus.share.user.store.businessTime"
        const val API_CAMPUS_LOGIN = "mtop.tmall.campus.member.user.login"

        /** 自检用的已知饮水机资源号（5 楼西那台），只读查询。 */
        const val PROBE_RES_NO = "630102040"

        /** 本校常量（实测：仰山/观澜公寓一带，换校区要重抓）。 */
        const val BUILDING_ID = 3336
        const val RELATIONSHIP_ID = 10915
    }
}

/**
 * 二维码解析。
 *
 * 实测原文：`https://share.confong.cn/xh?id=tmxy869451084947543`
 *
 * `/xh` 只是个静态引导页（「请使用天猫校园 APP 扫描二维码」），真正的解析在 App 内，
 * 所以只要能认出 `id=` 或裸 `tmxy<数字>` 就够了。
 */
object QrCodec {

    private val RE_TMXY = Regex("""tmxy(\d{10,20})""")
    private val RE_BARE = Regex("""^\s*(\d{9,20})\s*$""")

    /** 解析失败返回 null（不是饮水机二维码）。 */
    fun parse(text: String): ScannedDevice? {
        val raw = text.trim()
        if (raw.isEmpty()) return null

        // 1) URL 的 id / deviceCode 参数
        val fromUrl = runCatching {
            val q = raw.substringAfter('?', "")
            if (q.isEmpty()) "" else q.split('&').firstNotNullOfOrNull { pair ->
                val k = pair.substringBefore('=')
                if (k == "id" || k == "deviceCode") pair.substringAfter('=', "") else null
            } ?: ""
        }.getOrDefault("")

        // 2) 正则兜底：整串里捞 tmxy<数字>；再不济整串就是纯数字
        val code = when {
            fromUrl.isNotBlank() -> fromUrl
            RE_TMXY.containsMatchIn(raw) -> "tmxy" + RE_TMXY.find(raw)!!.groupValues[1]
            RE_BARE.matches(raw) -> RE_BARE.find(raw)!!.groupValues[1]
            else -> return null
        }
        return ScannedDevice(raw = raw, code = code)
    }
}
