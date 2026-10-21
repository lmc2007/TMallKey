package com.fktm.app.water

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录态（cookie）的本地保险箱。
 *
 * ## 为什么不能明文存
 *
 * `cookie2` 等同密码：拿到它就等于拿到了淘宝登录态，可以直接下单、看订单。
 * 明文 SharedPreferences 在 root 设备上、或备份/调试时都可能被端走。
 *
 * 所以用 **Android Keystore 里的 AES-256-GCM** 加密后落盘：
 * 密钥不出 TEE/StrongBox，App 卸载即销毁；即使拿到密文文件，换设备也解不开。
 *
 * ## 只存 mtop 需要的那几项
 *
 * 不整锅端走浏览器的 cookie，只留 `cookie2` / `_tb_token_` / `_nk_` / `munb` /
 * `tfstk` / `t` / `cna` 这一类；日志里也**只打印名字与数量，绝不打印值**。
 */
object WaterCookies {

    private const val TAG = "fkTM"
    private const val PREF = "water_auth"
    private const val KEY_CIPHERTEXT = "cookies_v1"
    private const val KEY_SAVED_AT = "saved_at"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "fktm_water_cookie_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /**
     * 明确不存的：体积大又没用的埋点类 cookie。
     *
     * ⚠ 别再用白名单 —— 实测淘宝登录会下发 `sg` / `sgcookie` / `wk_cookie2` /
     *   `_w_tb_nick` / `ultraCookieBase` 等，少了哪一个都可能让校园侧认不出身份。
     */
    private val DENY = setOf(
        "tfstk",        // 几 KB 的追踪栈，mtop 用不到
        "_samesite_flag_", "cancelledSubSites", "APLUS_LS_KEY",
    )

    fun save(context: Context, jar: Map<String, String>) {
        val kept = jar.filterKeys { it !in DENY }
        if (kept.isEmpty()) {
            Log.w(TAG, "water cookies: nothing to save")
            return
        }
        runCatching {
            val payload = JSONObject().apply {
                kept.forEach { (k, v) -> put(k, v) }
            }.toString()
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ct = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val blob = cipher.iv + ct
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(blob, Base64.NO_WRAP))
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "water cookies: saved ${kept.size} ${kept.keys.sorted()}")
        }.onFailure { Log.w(TAG, "water cookies: save failed", it) }
    }

    fun load(context: Context): Map<String, String>? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val encoded = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return runCatching {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, 12)
            val ct = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val json = String(cipher.doFinal(ct), Charsets.UTF_8)
            val obj = JSONObject(json)
            val jar = linkedMapOf<String, String>()
            obj.keys().forEach { k -> jar[k] = obj.optString(k) }
            jar
        }.onFailure { Log.w(TAG, "water cookies: load failed（密钥可能已失效）", it) }
            .getOrNull()
    }

    fun savedAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_SAVED_AT, 0L)

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
