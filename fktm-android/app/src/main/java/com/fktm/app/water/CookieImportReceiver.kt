package com.fktm.app.water

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * 接收 LSPatch 模块（`lspatch-module`）发来的登录态。
 *
 * 模块跑在**天猫校园进程内**，所以它能读到自己的 `CookieManager`：
 * 我们外面拿到的是加密后的 JSON，这里解密前先做最小校验，然后交给 [WaterSession] 加密落盘。
 *
 * 协议：
 * ```
 * action : com.fktm.app.ACTION_COOKIES
 * extra  : cookies_json = {"cookie2":"…","unb":"…"}
 * ```
 *
 * ⚠ 广播只由模块用 `setPackage("com.fktm.app")` 发来，其它应用理论上也能伪造；
 *   但伪造的 cookie 只会让他自己用不了，不会泄露任何东西，所以没加签名校验。
 */
class CookieImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COOKIES) return
        val raw = intent.getStringExtra(EXTRA_JSON) ?: return

        val jar = runCatching {
            val obj = JSONObject(raw)
            val map = linkedMapOf<String, String>()
            obj.keys().forEach { k ->
                val v = obj.optString(k)
                if (v.isNotBlank()) map[k] = v
            }
            map
        }.getOrNull()

        if (jar.isNullOrEmpty()) {
            Log.w(TAG, "cookie import: empty payload")
            return
        }

        // 必须有账号标识，否则可能是匿名 cookie
        val account = WaterSession.accountId(jar)
        if (account.isBlank()) {
            Log.w(TAG, "cookie import: no unb/munb, ignored")
            return
        }

        WaterSession.adopt(context.applicationContext, jar)
        WaterDiag.log(
            context.applicationContext, "importFromModule",
            WaterDiag.cookieSummary(jar),
        )
        Log.d(TAG, "cookie import: ${jar.size} cookies from LSPatch module")
    }

    companion object {
        private const val TAG = "fkTM"
        const val ACTION_COOKIES = "com.fktm.app.ACTION_COOKIES"
        const val EXTRA_JSON = "cookies_json"
    }
}
