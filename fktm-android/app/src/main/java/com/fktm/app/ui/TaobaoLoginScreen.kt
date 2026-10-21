package com.fktm.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fktm.app.water.AliLogin
import com.fktm.app.water.MtopClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 淘宝账号登录（内嵌官方 H5 登录页）。
 *
 * 天猫校园支持这条路径：`UCCApiManger.trustLogin(activity, "taobao")` +
 * `WebTaoBaoLoginInterceptor`，登录成功后由 `mtop.tmall.campus.member.user.login`
 * （**无入参**）把淘宝会话换成校园用户。
 *
 * 相比手机号 + 短信（smtp 那套要过滑块、还要 Aliyun 安全 SDK），这条路省事得多：
 * 登录页是官方的，风控参数由页面自己生成，我们只负责收 cookie。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TaobaoLoginScreen(
    onResult: (Map<String, String>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("淘宝登录") }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = MtopClient.USER_AGENT
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    loading = true
                    error = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    title = view?.title?.takeIf { it.isNotBlank() } ?: "淘宝登录"
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val u = request?.url?.toString() ?: return true
                    // 非 http(s) 一律拦掉，否则会拉起淘宝 App / 浏览器
                    return !(u.startsWith("http://") || u.startsWith("https://"))
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?,
                ): Boolean = false
            }
        }
    }

    DisposableEffect(Unit) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl(START_URL)
        onDispose {
            runCatching { cm.flush() }
            runCatching {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        }
    }

    /** 收 cookie → 换校园用户 → 交给上层保存。 */
    fun finish() {
        if (busy) return
        val jar = harvestAll()
        val account = jar["unb"] ?: jar["munb"]
        if (account.isNullOrBlank()) {
            error = "还没登录成功（没拿到账号标识），请先在上面的页面完成登录"
            return
        }
        busy = true
        error = null
        scope.launch {
            val campus = withContext(Dispatchers.IO) {
                AliLogin(MtopClient(LinkedHashMap(jar))).confirmCampusUser()
            }
            busy = false
            if (campus == null) {
                onResult(jar)
            } else {
                error = "淘宝登录成功，但校园侧没通过：$campus"
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
            Column(Modifier.weight(1f)) {
                Text("淘宝账号登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    title,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { finish() }, enabled = !busy) {
                Text(if (busy) "校验中…" else "我已完成")
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(4.dp))
        }

        error?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())

        Text(
            "密码只交给淘宝的登录页，本应用看不到；登录成功后仅在本机加密保存必要的 cookie。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** 把各域下的 cookie 全收（名字不筛，值只进内存）。 */
private fun harvestAll(): Map<String, String> {
    val cm = CookieManager.getInstance()
    runCatching { cm.flush() }
    val jar = linkedMapOf<String, String>()
    for (u in HARVEST_HOSTS) {
        val raw = runCatching { cm.getCookie(u) }.getOrNull() ?: continue
        for (part in raw.split(';')) {
            val k = part.substringBefore('=', "").trim()
            if (k.isEmpty()) continue
            jar[k] = part.substringAfter('=', "").trim()
        }
    }
    return jar
}

private val HARVEST_HOSTS = listOf(
    "https://www.taobao.com",
    "https://h5api.m.taobao.com",
    "https://m.taobao.com",
    "https://login.taobao.com",
    "https://share.confong.cn",
)

private const val START_URL =
    "https://login.taobao.com/member/login.jhtml?redirectURL=https%3A%2F%2Fwww.taobao.com%2F"
