package com.fktm.app.update

import android.content.Context

/**
 * 更新相关的偏好设置。
 *
 * 与 [com.fktm.app.credential.CredentialStore] 一样落在 SharedPreferences，
 * 但这里存的都是**非敏感**状态（时间戳、版本号、索引），不需要 Keystore 加密。
 */
class UpdateSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 上次检查时间（epoch millis）。用于启动自动检查的节流。 */
    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()

    /** 用户选择「忽略此版本」的 versionCode；-1 表示没有忽略任何版本。 */
    var ignoredVersionCode: Long
        get() = prefs.getLong(KEY_IGNORED_VERSION_CODE, -1L)
        set(value) = prefs.edit().putLong(KEY_IGNORED_VERSION_CODE, value).apply()

    /**
     * 上次成功拿到清单的源下标。
     * 下次检查从这个源开始（轮转到队首），避免每次都从已经挂掉的镜像开始试。
     */
    var preferredSourceIndex: Int
        get() = prefs.getInt(KEY_PREFERRED_SOURCE_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_SOURCE_INDEX, value).apply()

    var downloadMode: DownloadMode
        get() = enumOrDefault(prefs.getString(KEY_DOWNLOAD_MODE, null), DownloadMode.PROXY)
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_MODE, value.name).apply()

    var launchThreshold: LaunchCheckThreshold
        get() = enumOrDefault(prefs.getString(KEY_LAUNCH_THRESHOLD, null), LaunchCheckThreshold.DAYS_3)
        set(value) = prefs.edit().putString(KEY_LAUNCH_THRESHOLD, value.name).apply()

    /** 距上次检查是否已超过阈值。`MANUAL` 永远返回 false（不自动检查）。 */
    fun shouldCheckOnLaunch(now: Long = System.currentTimeMillis()): Boolean {
        val hours = launchThreshold.hours ?: return false
        val intervalMillis = hours * 60L * 60L * 1000L
        return now - lastCheckAt >= intervalMillis
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        if (raw.isNullOrEmpty()) fallback
        else runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

    companion object {
        private const val PREFS = "fktm_update"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_IGNORED_VERSION_CODE = "ignored_version_code"
        private const val KEY_PREFERRED_SOURCE_INDEX = "preferred_source_index"
        private const val KEY_DOWNLOAD_MODE = "download_mode"
        private const val KEY_LAUNCH_THRESHOLD = "launch_threshold"
    }
}
