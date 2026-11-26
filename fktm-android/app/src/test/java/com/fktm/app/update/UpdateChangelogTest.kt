package com.fktm.app.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChangelogTest {
    private val source = UpdateSource("test", "https://example.com/stable.json", 1000)

    private fun manifest(code: Long, extra: String = "") = UpdateManifestParser.parse(
        """{"versionName":"2.1.8","versionCode":$code,"releaseNotes":"目标版本摘要"$extra}""",
    )

    @Test fun legacyReleaseCanBeShownBeforeAnyLogRequest() {
        val release = manifest(17).toRelease(source, installedCode = 14)
        assertTrue(release.changelogLoading)
        assertEquals("目标版本摘要", release.changelog)
        assertEquals(17L, release.versionCode)
    }

    @Test fun packagedHistoryShowsOnlyUpgradeRangeNewestFirst() = runBlocking {
        val target = manifest(25, """, "changelogs": {"25":"# v2.1.8\n新版", "10":"已安装", "14":"# v2.1.1\n中间版本", "30":"未发布", "bad":"无效版本"}""")
        val result = loadUpdateChangelog(10, target.toRelease(source, installedCode = 10)) { error("Packaged history needs no requests") }
        assertFalse(result.changelogLoading)
        assertEquals("# v2.1.8\n新版\n\n# v2.1.1\n中间版本", result.changelog)
    }

    @Test fun legacyManifestFetchesEveryVersionInUpgradeRange() = runBlocking {
        val result = loadUpdateChangelog(14, manifest(17).toRelease(source, installedCode = 14)) { code -> "# build $code\n内容 $code" }
        assertFalse(result.changelogLoading)
        assertEquals("# build 17\n内容 17\n\n# build 16\n内容 16\n\n# build 15\n内容 15", result.changelog)
    }

    @Test fun unavailableLegacyLogShowsGapAndPreservesTargetNotes() = runBlocking {
        val result = loadUpdateChangelog(14, manifest(17).toRelease(source, installedCode = 14)) { if (it == 15L) "# v2.1.4\n历史日志" else null }
        assertTrue(result.changelog.startsWith("# v2.1.8\n目标版本摘要\n\n# v2.1.4\n历史日志"))
        assertTrue(result.changelog.contains("部分版本的完整更新日志暂不可用"))
    }

    @Test fun noUpgradeMakesNoChangelogRequests() = runBlocking {
        val release = manifest(17).toRelease(source, installedCode = 17)
        assertFalse(release.changelogLoading)
        assertEquals(release, loadUpdateChangelog(17, release) { error("Already up to date") })
    }

    @Test fun sparseLegacyRangeDoesNotProbeAnUnboundedNumberOfFiles() = runBlocking {
        val result = loadUpdateChangelog(1, manifest(Int.MAX_VALUE.toLong()).toRelease(source, installedCode = 1)) { "日志 $it" }
        assertTrue(result.changelog.contains("日志 ${Int.MAX_VALUE}"))
        assertTrue(result.changelog.contains("部分版本的完整更新日志暂不可用"))
        assertFalse(result.changelog.contains("日志 2\n"))
    }
}
