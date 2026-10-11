package com.fktm.app.update

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新仓库 —— 多镜像回退的检查逻辑与下载安装入口。
 *
 * 核心行为（与 yamibo-app 的 DefaultAppUpdateRepository 对齐）：
 *  1. 源列表有序，**上次成功的源轮转到队首**，避免每次从挂掉的镜像开始试；
 *  2. 逐个尝试，网络错 / 非 2xx / JSON 解析失败 → 记 error 换下一个源；
 *  3. **陈旧镜像守卫**：`versionCode ≤ 当前` 时不采信，继续扫剩余源，
 *     而不是草率地报「已是最新」；
 *  4. 命中 `versionCode > 当前` 且未被忽略 → 立刻返回可更新；
 *  5. 全部扫完仍无更新，才返回 UpToDate / Preparing / Failed。
 */
class AppUpdateRepository(
    context: Context,
    private val settings: UpdateSettings,
    private val installer: ApkInstaller,
) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val packageInfo =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)

    /** 当前安装包的 versionCode，用于与清单比对。 */
    @Suppress("DEPRECATION")
    val currentVersionCode: Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode
        else packageInfo.versionCode.toLong()

    val currentVersionName: String = packageInfo.versionName ?: "0"

    val sources: List<UpdateSource> = UpdateSources.all

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState

    // ---------------------------------------------------------------- 检查更新

    /**
     * 检查更新。
     *
     * @param force 用户手动点「检查更新」时为 true —— 会绕过「忽略此版本」。
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult {
        settings.lastCheckAt = System.currentTimeMillis()

        val start = settings.preferredSourceIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0))
        val ordered = sources.drop(start) + sources.take(start)

        val errors = mutableListOf<String>()
        var sawManifest = false
        var firstSuccessful: UpdateSource? = null
        var preparing: UpdateCheckResult.Preparing? = null
        var preparingIndex: Int? = null
        var ignored: UpdateRelease? = null

        for (source in ordered) {
            val manifest = try {
                fetchManifest(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "未知错误"
                Log.w(TAG, "清单拉取失败 source=${source.name}: $reason")
                errors += "${source.name}: $reason"
                continue
            }

            sawManifest = true
            if (firstSuccessful == null) firstSuccessful = source

            if (!manifest.isReady) {
                // 版本在灰度中：只提示，不提供下载
                if (manifest.versionCode > currentVersionCode && preparing == null) {
                    preparing = UpdateCheckResult.Preparing(
                        channel = manifest.channel,
                        versionName = manifest.versionName,
                        versionCode = manifest.versionCode,
                        sourceName = source.name,
                    )
                    preparingIndex = sources.indexOf(source).coerceAtLeast(0)
                }
                continue
            }

            val release = manifest.toRelease(source, fetchChangelog(source, manifest.versionCode))

            when {
                // 陈旧镜像：这个源的版本不比当前新，不能据此断定「已是最新」，继续扫
                release.versionCode <= currentVersionCode -> {
                    Log.d(TAG, "源已过期，继续扫 source=${source.name} version=${release.versionCode}")
                }

                !force && settings.ignoredVersionCode == release.versionCode -> {
                    if (ignored == null) ignored = release
                }

                else -> {
                    settings.preferredSourceIndex = sources.indexOf(source).coerceAtLeast(0)
                    return UpdateCheckResult.UpdateAvailable(release)
                }
            }
        }

        preparing?.let {
            preparingIndex?.let { index -> settings.preferredSourceIndex = index }
            return it
        }
        ignored?.let {
            settings.preferredSourceIndex = sources.indexOf(it.source).coerceAtLeast(0)
            return UpdateCheckResult.Ignored(it)
        }
        if (sawManifest) {
            firstSuccessful?.let { source ->
                settings.preferredSourceIndex = sources.indexOf(source).coerceAtLeast(0)
            }
            return UpdateCheckResult.UpToDate(currentVersionName)
        }
        return UpdateCheckResult.Failed(errors.joinToString("\n").ifBlank { "没有可用的更新源" })
    }

    private suspend fun fetchManifest(source: UpdateSource): UpdateManifest =
        withContext(Dispatchers.IO) {
            UpdateManifestParser.parse(httpGetText(source.manifestUrl, source.timeoutMillis))
        }

    private suspend fun fetchChangelog(source: UpdateSource, versionCode: Long): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = UpdateManifestParser.resolveChangelogUrl(source.manifestUrl, versionCode)
                httpGetText(url, source.timeoutMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "更新日志拉取失败 source=${source.name} v=$versionCode: ${e.message}")
                null
            }
        }

    private fun httpGetText(url: String, timeoutMillis: Long): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis.toInt()
            readTimeout = timeoutMillis.toInt()
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // 加速镜像挂掉时经常返回 HTML 错误页，这里挡掉，免得被当成清单去解析
            if (text.trimStart().startsWith("<")) throw IOException("返回了 HTML 而不是 JSON")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun UpdateManifest.toRelease(source: UpdateSource, changelog: String?): UpdateRelease {
        val picked = UpdateManifestParser.pickAsset(this)
        return UpdateRelease(
            source = source,
            channel = channel,
            versionName = versionName,
            versionCode = versionCode,
            minVersionCode = minVersionCode,
            releaseNotes = releaseNotes.orEmpty(),
            releaseUrl = releaseUrl ?: source.manifestUrl,
            asset = picked?.let { UpdateAsset(it.type, it.url, it.sha256, it.size) },
            changelogText = changelog.orEmpty(),
        )
    }

    // ---------------------------------------------------------------- 下载安装

    suspend fun downloadAndInstall(release: UpdateRelease): UpdateDownloadState {
        if (release.asset == null) {
            val failed = UpdateDownloadState.Failed(release, "该版本没有提供可安装的 APK")
            _downloadState.value = failed
            return failed
        }
        _downloadState.value = UpdateDownloadState.Running(release, 0L, release.asset.size)
        val result = installer.downloadAndInstall(release) { downloaded, total ->
            _downloadState.value =
                UpdateDownloadState.Running(release, downloaded, total ?: release.asset.size)
        }
        _downloadState.value = result
        return result
    }

    fun cancelDownload() {
        installer.cancelDownload()
        _downloadState.value = UpdateDownloadState.Idle
    }

    /** 用户选择「忽略此版本」——记录 versionCode，之后自动检查会跳过它。 */
    fun ignoreRelease(release: UpdateRelease) {
        settings.ignoredVersionCode = release.versionCode
    }

    /** 打开发布页（浏览器）。 */
    fun openReleasePage(release: UpdateRelease) {
        installer.openUrl(release.releaseUrl)
    }

    /** 是否已获得「安装未知应用」权限。 */
    val isInstallPermissionGranted: Boolean
        get() = installer.isInstallPermissionGranted

    private companion object {
        const val TAG = "AppUpdate"
        const val USER_AGENT = "TMKey-Updater"
    }
}
