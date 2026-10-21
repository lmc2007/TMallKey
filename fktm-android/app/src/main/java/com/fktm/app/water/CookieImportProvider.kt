package com.fktm.app.water

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

/**
 * 给 LSPatch 模块用的「推Cookie」入口。
 *
 * ## 为什么不用广播
 *
 * 模块跑在天猫校园进程里，fkTM 在后台时会被 ColorOS 冻结（`OplusHansManager`），
 * **冻结中的 App 收不到广播**（实测：前台能收、后台静默丢弃）。
 * 而 ContentProvider 调用会**主动拉起目标进程**，冻结拦不住。
 *
 * ## 协议
 *
 * ```
 * authority : com.fktm.app.cookies
 * call("put", null, Bundle{ cookies_json = "{...}" })
 * ```
 *
 * exported=true 意味着其它应用也能伪造调用，但伪造的 cookie 只会让他自己用不了，
 * 不会泄露任何东西。
 */
class CookieImportProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_PUT) return null
        val raw = extras?.getString(EXTRA_JSON) ?: return null

        val jar = runCatching {
            val obj = JSONObject(raw)
            val map = linkedMapOf<String, String>()
            obj.keys().forEach { k ->
                val v = obj.optString(k)
                if (v.isNotBlank()) map[k] = v
            }
            map
        }.getOrNull().orEmpty().filterKeys { it.isNotBlank() }

        val account = WaterSession.accountId(jar)
        val ctx = requireNotNull(context)
        if (account.isBlank()) {
            WaterDiag.log(ctx.applicationContext, "providerImport", "no unb/munb, ignored")
            return Bundle().apply { putBoolean("ok", false) }
        }

        // CM 层探测不过就不覆盖（新会话可能没绑校园档案，water.start 会失败）
        if (!WaterSession.campusProbeOk(jar)) {
            WaterDiag.log(ctx.applicationContext, "providerImport", "CM 层未绑定，忽略")
            return Bundle().apply { putBoolean("ok", false) }
        }
        WaterSession.adopt(ctx.applicationContext, jar)
        WaterDiag.log(ctx.applicationContext, "providerImport", WaterDiag.cookieSummary(jar))
        return Bundle().apply { putBoolean("ok", true) }
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? = null
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null

    companion object {
        const val AUTHORITY = "com.fktm.app.cookies"
        const val METHOD_PUT = "put"
        const val EXTRA_JSON = "cookies_json"
    }
}
