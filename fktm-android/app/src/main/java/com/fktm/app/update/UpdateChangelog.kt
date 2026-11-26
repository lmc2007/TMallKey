package com.fktm.app.update

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val LEGACY_VERSION_LIMIT = 32
private const val LEGACY_PARALLEL_REQUESTS = 8

/** 仅使用已取得的清单。旧清单的日志在显示更新提示后单独读取。 */
internal fun UpdateManifest.toRelease(source: UpdateSource, installedCode: Long): UpdateRelease {
    val hasHistory = versionCode in changelogs
    val history = if (hasHistory) {
        changelogs.entries
            .filter { (code, _) -> code > installedCode && code <= versionCode }
            .sortedByDescending { it.key }
            .joinToString("\n\n") { (code, text) -> formatChangelog(code, versionCode, versionName, text) }
    } else ""

    return UpdateRelease(
        source = source,
        channel = channel,
        versionName = versionName,
        versionCode = versionCode,
        minVersionCode = minVersionCode,
        releaseNotes = releaseNotes.orEmpty(),
        releaseUrl = releaseUrl ?: source.manifestUrl,
        asset = UpdateManifestParser.pickAsset(this)?.let { UpdateAsset(it.type, it.url, it.sha256, it.size) },
        changelogText = history,
        changelogLoading = versionCode > installedCode && !hasHistory,
    )
}

internal suspend fun loadUpdateChangelog(
    installedCode: Long,
    target: UpdateRelease,
    fetchLegacy: suspend (Long) -> String?,
): UpdateRelease {
    val targetCode = target.versionCode
    if (!target.changelogLoading || targetCode <= installedCode) return target

    // 旧清单没有日志表，限制逐文件探测次数，避免异常版本号造成大量请求。
    val lowerBound = maxOf(installedCode, targetCode - LEGACY_VERSION_LIMIT)
    val codes = (targetCode downTo (lowerBound + 1)).toList()
    val logs = coroutineScope {
        codes.chunked(LEGACY_PARALLEL_REQUESTS).flatMap { batch ->
            batch.map { code -> async { code to fetchLegacy(code)?.trim().orEmpty() } }.awaitAll()
        }
    }
    val sections = logs.mapNotNull { (code, text) ->
        when {
            text.isNotBlank() -> formatChangelog(code, targetCode, target.versionName, text)
            code == targetCode && target.releaseNotes.isNotBlank() -> formatChangelog(code, targetCode, target.versionName, target.releaseNotes.trim())
            else -> null
        }
    }
    val partial = lowerBound > installedCode || logs.any { (_, text) -> text.isBlank() }
    val notice = "部分版本的完整更新日志暂不可用，请查看 GitHub 发布记录。"
    return target.copy(
        changelogText = (if (partial) sections + notice else sections).joinToString("\n\n"),
        changelogLoading = false,
    )
}

private fun formatChangelog(code: Long, targetCode: Long, targetName: String, text: String): String {
    if (text.lineSequence().first().startsWith("# ")) return text
    val title = if (code == targetCode) "v$targetName" else "build $code"
    return "# $title\n$text"
}
