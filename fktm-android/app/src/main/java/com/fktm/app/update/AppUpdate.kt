package com.fktm.app.update

/*
 * 更新体系的数据模型。
 *
 * 语义对齐 yamibo-app 的 repository/appupdate 包（同一套「清单驱动 + 多镜像回退」思路），
 * 但去掉了 KMP / ktor / i18n 依赖，改成普通 Android 单模块实现。
 */

/** 一个更新源（加速镜像或 GitHub 直连）。[timeoutMillis] 是该源的硬超时。 */
data class UpdateSource(
    val name: String,
    val manifestUrl: String,
    val timeoutMillis: Long,
)

/** 清单里声明的可安装文件。Android 只认 `apk` / `universal-apk`。 */
data class UpdateAsset(
    val type: String,
    val url: String,
    val sha256: String?,
    val size: Long?,
)

/** 一个可用的新版本。 */
data class UpdateRelease(
    val source: UpdateSource,
    val channel: String,
    val versionName: String,
    val versionCode: Long,
    val minVersionCode: Long?,
    val releaseNotes: String,
    val releaseUrl: String,
    val asset: UpdateAsset?,
    /** 远端 changelog 正文；拉不到时为空串，[changelog] 会自动回落到 releaseNotes。 */
    val changelogText: String = "",
) {
    val changelog: String get() = changelogText.ifBlank { releaseNotes }
}

/** 检查结果，五态。 */
sealed interface UpdateCheckResult {
    /** 有更新的正式版本可以装。 */
    data class UpdateAvailable(val release: UpdateRelease) : UpdateCheckResult

    /** 已是最新。 */
    data class UpToDate(val currentVersionName: String) : UpdateCheckResult

    /** 有更新，但用户此前选了「忽略此版本」（重新手动检查时会绕过）。 */
    data class Ignored(val release: UpdateRelease) : UpdateCheckResult

    /**
     * 清单上版本更高，但 `isReady=false` —— 说明新版本正在灰度/尚未上传完成，
     * 此时只提示，不提供安装，避免把用户导向一个不存在的 APK。
     */
    data class Preparing(
        val channel: String,
        val versionName: String,
        val versionCode: Long,
        val sourceName: String,
    ) : UpdateCheckResult

    /** 所有源都失败。message 里是逐源错误摘要。 */
    data class Failed(val message: String) : UpdateCheckResult
}

/** 下载 / 安装状态，五态。 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Running(
        val release: UpdateRelease,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : UpdateDownloadState

    data class Completed(val release: UpdateRelease) : UpdateDownloadState

    /** 已下载完，但缺「安装未知应用」权限，需要用户去系统设置里授权。 */
    data class PermissionRequired(val release: UpdateRelease) : UpdateDownloadState

    data class Failed(val release: UpdateRelease?, val message: String) : UpdateDownloadState
}

/** APK 下载方式。 */
enum class DownloadMode(val label: String) {
    /** 默认：先走第三方 GitHub 加速代理，失败再回落直连。 */
    PROXY("代理优先"),

    /** 只走 GitHub 直连。 */
    DIRECT("GitHub 直连"),
}

/** 启动时自动检查的时间阈值。 */
enum class LaunchCheckThreshold(val label: String, val hours: Long?) {
    MANUAL("仅手动", null),
    HOURS_6("每 6 小时", 6),
    HOURS_12("每 12 小时", 12),
    HOURS_24("每 24 小时", 24),
    DAYS_3("每 3 天", 72),
    DAYS_7("每 7 天", 168),
}
