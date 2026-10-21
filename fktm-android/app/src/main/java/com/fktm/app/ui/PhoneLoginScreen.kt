package com.fktm.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fktm.app.water.AliLogin
import com.fktm.app.water.MtopClient
import com.fktm.app.water.WaterDiag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 天猫校园账号登录：手机号 + 短信验证码。
 *
 * 走的是校园自己的账号体系（阿里 UCC，`site=tmall_campus`），不是淘宝登录 ——
 * 天猫校园里的「绑定淘宝账号」是可选项，和登录是两件事。
 *
 * 三步，任何一步需要人机验证就切到滑块页：
 *   ① 发验证码 `mtop.taobao.mloginService.smsSend`
 *   ② （可选）punish 页面滑一下，拿到 `x5sec` cookie
 *   ③ `mtop.taobao.mloginService.smsLogin` 换 session
 */
@Composable
fun PhoneLoginScreen(
    onResult: (Map<String, String>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val client = remember { MtopClient(linkedMapOf()) }
    val ali = remember {
        val dm = context.resources.displayMetrics
        AliLogin(client, "${dm.widthPixels}x${dm.heightPixels}")
    }
    // 阿里风控要的设备指纹，由后台 WebView 里的 um.js 现算
    val umid = rememberUmidToken()

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var captchaUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    // 被限流后强制冷静，避免越点越糟
    var cooldown by remember { mutableIntStateOf(0) }

    /** 发码（可能需要先过滑块）。 */
    fun sendSms() {
        if (busy) return
        if (phone.length < 11) {
            error = "手机号要 11 位"
            return
        }
        busy = true
        error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { ali.sendSms(phone, umid) }
            WaterDiag.log(
                context.applicationContext, "smsSend",
                "umidLen=${umid.length} -> " + when (r) {
                    is AliLogin.SendResult.Sent -> "SENT"
                    is AliLogin.SendResult.NeedCaptcha -> "NEED_CAPTCHA"
                    is AliLogin.SendResult.Failed -> r.code
                },
            )
            busy = false
            when (r) {
                is AliLogin.SendResult.Sent -> {
                    codeSent = true
                    hint = "验证码已发送"
                }
                is AliLogin.SendResult.NeedCaptcha -> {
                    hint = "需要人机验证，滑动一下即可继续"
                    captchaUrl = r.url
                }
                is AliLogin.SendResult.Failed -> {
                    error = "${r.code}：${r.message}"
                    if (r.code == "RGV587_ERROR") {
                        cooldown = 120
                        while (cooldown > 0) {
                            delay(1000)
                            cooldown -= 1
                        }
                    }
                }
            }
        }
    }

    /** 登录并确认校园身份。 */
    fun doLogin() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val res = withContext(Dispatchers.IO) { ali.login(phone, code, umid) }
            res.onSuccess { jar ->
                val campus = withContext(Dispatchers.IO) { ali.confirmCampusUser() }
                busy = false
                if (campus != null) {
                    error = "登录成功，但校园侧不认这个账号：$campus"
                } else {
                    onResult(jar)
                }
            }.onFailure { e ->
                busy = false
                error = e.message
            }
        }
    }

    // ---- 滑块页 ----
    captchaUrl?.let { url ->
        CaptchaScreen(
            url = url,
            seed = client.export(),
            onCancel = { captchaUrl = null },
            onSolved = {
                // 把 punish 页种下的 x5sec 带给 native 请求
                client.merge(harvestCookies())
                captchaUrl = null
                sendSms()
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
                Text("返回")
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("校园账号登录", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "天猫校园账号（手机号），不是淘宝账号",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("手机号") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (codeSent) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("短信验证码") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = { if (codeSent) doLogin() else sendSms() },
                    enabled = !busy && cooldown == 0 && phone.length == 11 &&
                        (!codeSent || code.length >= 4),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp).height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        when {
                            busy -> "处理中…"
                            cooldown > 0 -> "${cooldown}s 后可重试"
                            codeSent -> "登录"
                            else -> "发送验证码"
                        },
                        fontSize = 16.sp,
                    )
                }

                if (codeSent) {
                    OutlinedButton(
                        onClick = { sendSms() },
                        enabled = !busy && cooldown == 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("重新发送验证码") }
                }
            }
        }

        Text(
            if (umid.isBlank()) "设备指纹生成中…（约 5-15 秒，生成后发送成功率更高）"
            else "设备指纹已就绪",
            fontSize = 11.sp,
            color = if (umid.isBlank()) Color(0xFFB07A2A) else Color(0xFF2E9963),
        )
        hint?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE06C6C).copy(alpha = 0.12f)
                ),
            ) {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = Color(0xFFC05050),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Text(
            "登录的是天猫校园账号（手机号），密码不经过本应用。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )
    }
}

/**
 * 阿里的人机验证页（滑块）。
 *
 * 滑完页面会种下 `x5sec` cookie，[onSolved] 之后带回去重试即可。
 * 这里每 600ms 探一次 cookie，探到就当作完成 —— 比解析页面的成功回调稳。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaScreen(
    url: String,
    seed: Map<String, String>,
    onCancel: () -> Unit,
    onSolved: () -> Unit,
) {
    val context = LocalContext.current
    var done by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = com.fktm.app.water.MtopClient.USER_AGENT
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = request?.url?.toString()?.startsWith("http") == false
            }
        }
    }

    DisposableEffect(Unit) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        // ★ 先清掉上一次遗留的 x5sec，否则轮询会立刻认为「已通过」，
        //   然后带着过期凭据重发 —— 实测就是这个导致一直 RGV587、滑块根本不出现。
        listOf("x5sec", "_x5sec").forEach { name ->
            runCatching { cm.setCookie(HOST, "$name=; Max-Age=0; path=/") }
            runCatching { cm.setCookie("https://www.taobao.com", "$name=; Max-Age=0; path=/") }
        }
        // 再把 native 请求拿到的 x5secdata 带给验证页，否则它会认为是另一个客户端
        seed.forEach { (k, v) ->
            runCatching { cm.setCookie(HOST, "$k=$v; path=/") }
        }
        runCatching { cm.flush() }
        webView.loadUrl(url)
        onDispose {
            runCatching { cm.flush() }
            runCatching {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel) { Text("取消") }
            Text(
                "滑完滑块后点右边",
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (!done) {
                        done = true
                        onSolved()
                    }
                },
            ) { Text("验证已完成") }
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/** 人机验证与 mtop 同域。 */
private const val HOST = "https://h5api.m.taobao.com"

/** 从 WebView 的 CookieManager 里收 x5sec 一类风控 cookie。 */
private fun harvestCookies(): Map<String, String> {
    val cm = CookieManager.getInstance()
    runCatching { cm.flush() }
    val jar = linkedMapOf<String, String>()
    for (u in listOf(HOST, "https://www.taobao.com")) {
        val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
        for (part in raw.split(';')) {
            val k = part.substringBefore('=', "").trim()
            if (k.isEmpty()) continue
            jar[k] = part.substringAfter('=', "").trim()
        }
    }
    return jar
}
