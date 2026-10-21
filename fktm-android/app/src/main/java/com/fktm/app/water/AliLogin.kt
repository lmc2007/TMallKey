package com.fktm.app.water

import org.json.JSONObject

/**
 * 天猫校园账号登录（阿里 UCC / AliUser 体系，手机号 + 短信验证码）。
 *
 * ## 为什么不是淘宝登录
 *
 * 反编译天猫校园 5.0.0 得到的事实：
 *  · `CUserLoginActivity` 里的 `SmsCodeFragment extends AliUserSMSLoginVerificationFragment`；
 *  · `UCCApiManger.AliMemberSDK.init(context, SITE)`，`SITE = "tmall_campus"`；
 *  · `uccService.bind(activity, "taobao")` 才是**可选**的淘宝绑定。
 *
 * 所以校园账号是阿里 UCC 账号，和淘宝账号是两套。同一个手机号下可能各有一个，
 * 用淘宝登录页只会登进淘宝那个（实测 22098***940 vs 校园 22230***759），
 * 校园侧就会回 `GET_CAMPUS_USER_ERROR`。
 *
 * ## 为什么需要人机验证
 *
 * 手工构造的请求没有 `umidToken`（阿里设备指纹，由 App 内的 SDK 生成），
 * 网关会回 `FAIL_SYS_USER_VALIDATE` + 一个 punish 地址。这不是失败：
 *  · 先调 [sendSms]，需要验证就把 punish 地址交给 WebView 让人滑一下；
 *  · 滑完页面会种 `x5sec` cookie，带上它重发就能过；
 *  · 然后 [login] 拿 session。
 *
 * ## umidToken 从哪来
 *
 * 不用破解 `libsgmainso`。阿里的 H5 侧 `um.js` 会自己算出 `bx-umidtoken`
 * （实测在 `login.m.taobao.com` 上能稳定取到，68 字符），把它带进请求即可：
 *  · 不带 → `RGV587_ERROR`（直接挡）
 *  · 带   → `FAIL_SYS_USER_VALIDATE`（只要求滑块，说明指纹被认可）
 *
 * ⚠ 这条链路每一步都可能被风控调整，所以每一步都把服务端原文留给 UI 显示。
 */
class AliLogin(
    private val client: MtopClient,
    /** 屏幕尺寸，风控字段之一（`1080x2376` 这种），由 UI 传入。 */
    private val screenSize: String = "1080x2400",
) {

    sealed interface SendResult {
        /** 短信已发出。 */
        data object Sent : SendResult
        /** 需要人机验证：把 [url] 丢给 WebView，滑动后带新 cookie 重试。 */
        data class NeedCaptcha(val url: String) : SendResult
        data class Failed(val code: String, val message: String) : SendResult
    }

    /**
     * 发送短信验证码。
     *
     * 实测原始请求（2026-10-20，返回 USER_VALIDATE 说明结构已被识别）：
     * ```
     * loginId=手机号, loginType=taobao, site=0, appName=tmall_campus,
     * countryCode=86, codeLength=6, locale=zh_CN, resendAlert=false,
     * ext={"apiVersion":"2.0","deviceName":<机型>,"sdkTraceId":"..."}
     * ```
     */
    fun sendSms(phone: String, umidToken: String = ""): SendResult = try {
        client.call(API_SEND_SMS, envelope(phone, umidToken))
        SendResult.Sent
    } catch (e: MtopError) {
        if (e.code == "FAIL_SYS_USER_VALIDATE") {
            val url = punishUrl(e)
            if (url != null) SendResult.NeedCaptcha(url)
            else SendResult.Failed(e.code, e.ret.joinToString(" | "))
        } else if (e.code == "RGV587_ERROR") {
            // 原文「哎哟喂,被挤爆啦」= 频率限流，不是指纹问题。继续猛点只会更糟。
            SendResult.Failed(e.code, "请求太频繁，被风控限流了。请等 10-15 分钟再试，不要连续点。")
        } else {
            SendResult.Failed(e.code, e.human())
        }
    }

    /**
     * 用验证码登录。
     *
     * 成功时服务端 `Set-Cookie` 下发 `cookie2` / `unb` —— [client] 会自动吸收，
     * 之后从它里面取出 jar 交给 [WaterSession.adopt] 即可。
     */
    fun login(phone: String, code: String, umidToken: String = ""): Result<Map<String, String>> = try {
        client.call(API_SMS_LOGIN, envelope(phone, umidToken, mapOf("smsCode" to code)))
        @Suppress("UNCHECKED_CAST")
        Result.success(client.export())
    } catch (e: MtopError) {
        Result.failure(IllegalStateException("${e.code}：${e.human()}", e))
    }

    /** 拿当前 session 换校园用户；返回 null 表示成功。 */
    fun confirmCampusUser(): String? = try {
        client.call(API_CAMPUS_LOGIN, emptyMap())
        null
    } catch (e: MtopError) {
        "${e.code}：${e.human()}"
    }

    /**
     * 组装请求。**天猫校园的真实结构是三个 JSON 字符串**，不是平铺字段。
     *
     * 来自 `com.ali.user.mobile.data.LoginComponent.buildSendSmsByLoginRequest()`：
     * ```
     * addParam("ext",            JSON.toJSONString(extMap))
     * addParam("loginInfo",      JSON.toJSONString(sMSLoginRequest))
     * addParam("riskControlInfo", JSON.toJSONString(buildWSecurityData()))
     * ```
     *
     * `riskControlInfo` 由 `SecurityGuardManagerWraper.buildWSecurityData()` 生成：
     * `{wua, apdId, umidToken, appStore, osName, osVersion,
     *   deviceModel, deviceBrand, deviceName, screenSize, t}`。
     * 其中 `umidToken` 我们用 H5 的 um.js 顶替（`wua`/`apdId` 需要安全 SDK，暂缺）。
     *
     * ⚠ 平铺发过去会拿到 `RGV587_ERROR`（被当成陌生客户端）；按这个结构发，
     *   才稳定回到「要滑块」的 `FAIL_SYS_USER_VALIDATE`。
     */
    private fun envelope(phone: String, umidToken: String, extra: Map<String, Any?> = emptyMap()):
        Map<String, Any?> {
        val now = System.currentTimeMillis()
        val ext = linkedMapOf<String, Any?>(
            "apiVersion" to "2.0",
            "deviceName" to android.os.Build.MODEL,
            "sdkTraceId" to now.toString(),
        )
        val loginInfo = linkedMapOf<String, Any?>(
            "loginId" to phone,
            "loginType" to LOGIN_TYPE,
            "site" to 0,
            "locale" to "zh_CN",
            "countryCode" to "86",
            "phoneCode" to "86",
            "codeLength" to "6",
            "t" to now,
        )
        loginInfo.putAll(extra)
        val risk = linkedMapOf<String, Any?>(
            "umidToken" to umidToken,
            "osName" to "android",
            "osVersion" to android.os.Build.VERSION.RELEASE,
            "deviceModel" to android.os.Build.MODEL,
            "deviceBrand" to android.os.Build.MANUFACTURER,
            "deviceName" to android.os.Build.MODEL,
            "screenSize" to screenSize,
            "t" to now.toString(),
        )
        return linkedMapOf(
            "ext" to WaterJson.stringify(ext),
            "loginInfo" to WaterJson.stringify(loginInfo),
            "riskControlInfo" to WaterJson.stringify(risk),
        )
    }

    /** 从 USER_VALIDATE 的响应里抠出 punish 地址。 */
    private fun punishUrl(e: MtopError): String? = runCatching {
        val body = e.payload
        val obj = JSONObject(body)
        obj.optJSONObject("data")?.optString("url")?.takeIf { it.startsWith("http") }
    }.getOrNull()

    companion object {
        /** 校园侧：阿里 UCC 的 site 标识，来自 `UCCApiManger.SITE`。 */
        const val APP_NAME = "tmall_campus"
        const val LOGIN_TYPE = "taobao"   // AccountType.TAOBAO_ACCOUNT = Site.TAOBAO

        const val API_SEND_SMS = "mtop.taobao.mloginService.smsSend"
        const val API_SMS_LOGIN = "mtop.taobao.mloginService.smsLogin"
        const val API_CAMPUS_LOGIN = "mtop.tmall.campus.member.user.login"

        /** 人机验证通过后要采集的 cookie 名。 */
        val CAPTCHA_COOKIES = listOf("x5sec", "_x5sec", "x5secdata", "x5sectag")
    }
}
