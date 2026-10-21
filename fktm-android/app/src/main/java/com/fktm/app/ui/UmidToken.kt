package com.fktm.app.ui

import android.annotation.SuppressLint
import android.os.Message
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fktm.app.water.MtopClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 取阿里风控用的 `umidToken`（**不碰 `libsgmainso`，不需要 JNI**）。
 *
 * ## 思路
 *
 * 阿里 H5 的 `um.js` 会自己算一个设备指纹，并以 `bx-umidtoken` 的形式挂在
 * `https://umdcv4.taobao.com/repWd.json?...&bx-umidtoken=<token>` 这条请求上。
 * 所以只要在后台开一个 WebView 打开任意会加载 um.js 的页面
 * （实测 `login.m.taobao.com/login.htm` 稳定可用），
 * 再从 `performance` 资源列表里把那个 token 抠出来即可。
 *
 * 效果（`smsSend` 实测）：
 *  · 不带 token → `RGV587_ERROR`，直接被挡
 *  · 带 token   → `FAIL_SYS_USER_VALIDATE`，只要求滑块 —— 说明指纹被认可
 *
 * WebView 只有 1dp，用户看不见，也不需要交互。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun rememberUmidToken(): String {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = MtopClient.USER_AGENT
            // ⚠ 必须设 WebViewClient：淘宝页面会跳 tbopen:// / intent:// 之类的外部 scheme，
            //   不拦的话系统会直接拉起浏览器（实测会打开 Edge 并跳到淘宝），
            //   页面随即离开，um.js 再也算不出指纹。
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = blockExternal(request?.url?.toString())

                @Deprecated("兼容旧 API")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    blockExternal(url)
            }
            webChromeClient = object : WebChromeClient() {
                // window.open / target=_blank 一律不新开窗口
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean = false
            }
            loadUrl(UMID_PAGE)
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { webView.destroy() } }
    }

    LaunchedEffect(Unit) {
        // um.js 是异步跑的，通常 5~15 秒内出 token；最多等 40 秒
        repeat(40) {
            if (token.isNotBlank()) return@LaunchedEffect
            if (it == 39) Log.w("fkTM", "umid: timeout, no bx-umidtoken found")
            delay(1_000)
            val js = "performance.getEntriesByType('resource').map(function(r){return r.name})" +
                ".filter(function(n){return n.indexOf('bx-umidtoken=')>=0}).join('|')"
            val raw = webView.evaluateJavascriptSuspend(js)
            val found = RE_UMID.find(raw)?.groupValues?.get(1)
            if (!found.isNullOrBlank()) {
                token = found
                Log.d("fkTM", "umid: token ready, len=${found.length}")
            }
        }
    }

    // 1dp 的宿主，纯粹为了驱动那个 WebView
    Box(Modifier.size(1.dp)) {
        AndroidView(factory = { webView })
    }
    return token
}

/** `evaluateJavascript` 的回调式 API 包成挂起函数。 */
private suspend fun WebView.evaluateJavascriptSuspend(script: String): String =
    suspendCancellableCoroutine { cont ->
        try {
            evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(value ?: "")
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (cont.isActive) cont.resume("")
        }
    }

/** 只放行 http(s)，其余 scheme 一律拦掉（否则会拉起系统浏览器/淘宝 App）。 */
private fun blockExternal(url: String?): Boolean {
    if (url == null) return true
    val low = url.lowercase()
    return !(low.startsWith("http://") || low.startsWith("https://"))
}

/** 从 `bx-umidtoken=<token>` 里取 token（可能是 URL 编码的）。 */
private val RE_UMID = Regex("""bx-umidtoken=([^&|]+)""")

/** 会加载 um.js 的页面（移动版登录页最轻，实测可用）。 */
private const val UMID_PAGE = "https://login.m.taobao.com/login.htm"
