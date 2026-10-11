package com.fktm.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * APK 下载与安装。
 *
 * 下载侧的防护（照搬 yamibo-app 的 AndroidAppUpdatePlatform）：
 *  · 落到 `cache/updates/`，已缓存且 sha256 一致的 APK 直接复用，省一次下载；
 *  · 校验 ZIP 魔数 `50 4B 03 04`，挡掉「返回 HTML 错误页」这种坏包；
 *  · 清单给了 sha256 就必须比对，不一致直接判失败；
 *  · 代理优先模式下逐个代理尝试，全失败再回落 GitHub 直连。
 *
 * 安装侧：先确认「安装未知应用」权限，再用 FileProvider 把 APK 交给系统安装器。
 */
class ApkInstaller(
    private val context: Context,
    private val settings: UpdateSettings,
) {
    @Volatile
    private var canceled = false

    val isInstallPermissionGranted: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

    fun cancelDownload() {
        canceled = true
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "打开链接失败: $url", it) }
    }

    suspend fun downloadAndInstall(
        release: UpdateRelease,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): UpdateDownloadState = withContext(Dispatchers.IO) {
        val asset = release.asset
            ?: return@withContext UpdateDownloadState.Failed(release, "该版本没有提供可安装的 APK")

        canceled = false
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(dir, "tmkey-${release.versionName}.apk")

        // 已经下过且校验通过 —— 直接进安装流程
        if (apkFile.isValidCachedApk(asset)) {
            return@withContext requestInstall(apkFile, release)
        }

        val urls = resolveDownloadUrls(asset.url, settings.downloadMode)
        if (urls.isEmpty()) {
            return@withContext UpdateDownloadState.Failed(release, "APK 地址为空")
        }

        var lastError: Throwable? = null
        for (url in urls) {
            if (canceled) {
                lastError = CancellationException()
                break
            }
            val attempt = runCatching { downloadTo(url, apkFile, asset, onProgress) }
            if (attempt.isSuccess) {
                lastError = null
                break
            }
            lastError = attempt.exceptionOrNull()
            apkFile.delete()
            if (lastError is CancellationException) break
            Log.w(TAG, "下载失败，切换下一个地址 url=$url", lastError)
        }

        if (lastError != null) {
            apkFile.delete()
            val message = if (lastError is CancellationException) "已取消" else lastError.message ?: "下载失败"
            return@withContext UpdateDownloadState.Failed(release, message)
        }
        requestInstall(apkFile, release)
    }

    private fun downloadTo(
        url: String,
        apkFile: File,
        asset: UpdateAsset,
        onProgress: (Long, Long?) -> Unit,
    ) {
        if (apkFile.exists()) apkFile.delete()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            if (conn.contentType.orEmpty().lowercase().contains("text/html")) {
                throw IOException("下载到的是 HTML 页面而不是 APK")
            }

            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    val total = asset.size ?: conn.contentLengthLong.takeIf { it >= 0L }
                    while (true) {
                        if (canceled) throw CancellationException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (!apkFile.hasApkZipSignature()) throw IOException("下载内容不是合法的 APK/ZIP")

        asset.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = apkFile.sha256()
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IOException("APK 校验失败：期望 $expected，实际 $actual")
            }
        }
    }

    private fun File.isValidCachedApk(asset: UpdateAsset): Boolean {
        if (!exists() || !hasApkZipSignature()) return false
        val expected = asset.sha256
        if (expected.isNullOrBlank()) return true
        return runCatching { sha256().equals(expected, ignoreCase = true) }
            .onFailure { Log.d(TAG, "缓存 APK 校验异常，改为重新下载", it) }
            .getOrDefault(false)
    }

    private fun requestInstall(apkFile: File, release: UpdateRelease): UpdateDownloadState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            // 没有「安装未知应用」权限，先把用户送到系统设置页
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".let(Uri::parse),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Log.w(TAG, "跳转安装权限设置失败", it) }
            return UpdateDownloadState.PermissionRequired(release)
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            UpdateDownloadState.Completed(release)
        }.getOrElse { error ->
            Log.w(TAG, "拉起系统安装器失败", error)
            UpdateDownloadState.Failed(release, "拉起安装器失败：${error.message ?: "未知错误"}")
        }
    }

    private companion object {
        const val TAG = "ApkInstaller"
        const val USER_AGENT = "TMKey-Updater"
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}

/**
 * 解析 APK 的候选下载地址。
 *
 * - GitHub 托管的资产：代理优先模式下先试每个加速节点，最后回落直连；
 *   直连模式下只返回原始地址。
 * - 其它来源（Gitee / 自建站等）保持原样直连。
 */
internal fun resolveDownloadUrls(
    assetUrl: String,
    mode: DownloadMode = DownloadMode.PROXY,
): List<String> {
    val url = assetUrl.trim()
    if (url.isEmpty()) return emptyList()

    val host = runCatching { URL(url).host.lowercase() }.getOrNull()
    val isGitHubHosted = host != null && (
            host == "github.com" ||
                    host == "objects.githubusercontent.com" ||
                    host.endsWith(".github.com")
            )
    if (!isGitHubHosted) return listOf(url)

    return when (mode) {
        DownloadMode.DIRECT -> listOf(url)
        DownloadMode.PROXY -> UpdateSources.DOWNLOAD_PROXIES.map { it + url } + url
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

/** APK 本质是 ZIP，头四字节必须是 `PK\x03\x04`。 */
private fun File.hasApkZipSignature(): Boolean =
    length() >= 4L && inputStream().use { input ->
        val header = ByteArray(4)
        input.read(header) == header.size &&
                header.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    }
