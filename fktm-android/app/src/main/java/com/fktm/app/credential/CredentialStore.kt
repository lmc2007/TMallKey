package com.fktm.app.credential

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * CredentialStore —— 凭证的本地持久化。
 *
 * 安全模型：
 *   · activationKey 用 **Android Keystore** 里的 AES-256-GCM 密钥加密后才落盘；
 *   · 该密钥**不可导出**（hardware-backed，若设备支持），App 卸载即销毁；
 *   · 密文 + IV 以 base64 存进 SharedPreferences；
 *   · Backup / 设备迁移已通过 data_extraction_rules 排除（钥匙不跟着走）。
 *
 * 这不是"混淆式"安全 —— 是真正的密钥隔离：即使 prefs 文件被拷走，
 * 没有该设备的 Keystore 也解不开。
 */
class CredentialStore(context: Context) {

    companion object {
        private const val TAG = "fkTMCred"
        private const val PREFS = "fktm_credentials"
        private const val KEY_ALIAS = "fktm_master_key"
        private const val FIELD_CREDENTIALS = "credentials_enc"
        private const val FIELD_IV = "credentials_iv"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128
        private val storeLock = Any()
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- Keystore ----------------

    private fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ct, Base64.NO_WRAP) to
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(ctB64: String, ivB64: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP))
        )
        return String(cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun loadStored(): List<LockCredential> {
        val ct = prefs.getString(FIELD_CREDENTIALS, null) ?: return emptyList()
        val iv = checkNotNull(prefs.getString(FIELD_IV, null))
        return LockCredentialJson.decode(decrypt(ct, iv))
    }

    fun load(): List<LockCredential> = synchronized(storeLock) {
        try {
            loadStored()
        } catch (_: Exception) {
            Log.w(TAG, "无法读取本机凭证")
            emptyList()
        }
    }

    // Backup operations must fail if the existing store cannot be read.
    internal fun loadForBackup(): List<LockCredential> = synchronized(storeLock) { loadStored() }

    /** Returns the number of new locks. Save the merged list in one encrypted write. */
    internal fun restoreBackup(imported: List<LockCredential>): Int = synchronized(storeLock) {
        val existing = loadStored()
        val merged = CredentialBackup.merge(existing, imported)
        val added = merged.size - existing.size
        if (added > 0) persist(merged)
        added
    }

    private fun persist(list: List<LockCredential>) {
        val (ct, iv) = encrypt(LockCredentialJson.encode(list))
        prefs.edit().putString(FIELD_CREDENTIALS, ct).putString(FIELD_IV, iv).apply()
    }

    /** 主锁（列表第一把）。 */
    fun primary(): LockCredential? = load().firstOrNull()

    /** 新增或按 address 覆盖。 */
    fun upsert(cred: LockCredential) = synchronized(storeLock) {
        val list = load().filterNot { it.address.equals(cred.address, ignoreCase = true) }.toMutableList()
        list.add(0, cred)
        persist(list)
    }

    fun remove(address: String) = synchronized(storeLock) {
        persist(load().filterNot { it.address.equals(address, ignoreCase = true) })
    }

    fun clear() = synchronized(storeLock) {
        prefs.edit().clear().apply()
    }

    fun hasAny(): Boolean = load().isNotEmpty()
}
