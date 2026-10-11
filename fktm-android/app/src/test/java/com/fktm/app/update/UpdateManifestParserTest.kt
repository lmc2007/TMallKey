package com.fktm.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 更新清单解析与下载地址解析的回归测试。
 *
 * 这两块是纯函数，不依赖 Android 运行时，可以直接在 JVM 上跑。
 * 清单格式一旦改动，这里必须先红。
 */
class UpdateManifestParserTest {

    private val validManifest = """
        {
          "channel": "stable",
          "versionName": "1.0.1",
          "versionCode": 2,
          "isReady": true,
          "minVersionCode": 1,
          "releaseNotes": "修了一个小问题",
          "releaseUrl": "https://github.com/lmc2007/TMallKey/releases/tag/v1.0.1",
          "assets": [
            {
              "type": "universal-apk",
              "platform": "android",
              "url": "https://github.com/lmc2007/TMallKey/releases/download/v1.0.1/TMKey-1.0.1.apk",
              "sha256": "aabbccdd",
              "size": 12345678
            }
          ]
        }
    """.trimIndent()

    // ------------------------------------------------------------ 正常解析

    @Test
    fun parsesAllFields() {
        val m = UpdateManifestParser.parse(validManifest)

        assertEquals("stable", m.channel)
        assertEquals("1.0.1", m.versionName)
        assertEquals(2L, m.versionCode)
        assertTrue(m.isReady)
        assertEquals(1L, m.minVersionCode)
        assertEquals("修了一个小问题", m.releaseNotes)
        assertEquals("https://github.com/lmc2007/TMallKey/releases/tag/v1.0.1", m.releaseUrl)
        assertEquals(1, m.assets.size)

        val asset = m.assets.first()
        assertEquals("universal-apk", asset.type)
        assertEquals("android", asset.platform)
        assertEquals("aabbccdd", asset.sha256)
        assertEquals(12345678L, asset.size)
    }

    @Test
    fun defaultsChannelToStableWhenAbsent() {
        val m = UpdateManifestParser.parse("""{"versionName":"1.0.0","versionCode":1}""")
        assertEquals("stable", m.channel)
        assertNull(m.releaseNotes)
        assertNull(m.releaseUrl)
        assertTrue(m.assets.isEmpty())
    }

    @Test
    fun sourceManifestWithIsReadyFalseIsAccepted() {
        // 灰度中的清单：能解析，但 isReady=false，由仓库层决定不给下载
        val m = UpdateManifestParser.parse(
            """{"versionName":"2.0.0","versionCode":9,"isReady":false,"assets":[]}"""
        )
        assertEquals(false, m.isReady)
        assertEquals(9L, m.versionCode)
    }

    @Test
    fun skipsAssetsWithoutUrl() {
        val m = UpdateManifestParser.parse(
            """
            {"versionName":"1.0.0","versionCode":1,"assets":[
              {"type":"apk","url":"   "},
              {"type":"apk","url":"https://example.com/a.apk"}
            ]}
            """.trimIndent()
        )
        assertEquals(1, m.assets.size)
        assertEquals("https://example.com/a.apk", m.assets.first().url)
    }

    @Test
    fun blankSha256AndSizeBecomeNull() {
        val m = UpdateManifestParser.parse(
            """
            {"versionName":"1.0.0","versionCode":1,"assets":[
              {"type":"apk","url":"https://example.com/a.apk","sha256":"","size":0}
            ]}
            """.trimIndent()
        )
        assertNull(m.assets.first().sha256)
        assertNull(m.assets.first().size)
    }

    // ------------------------------------------------------------ 非法输入

    @Test
    fun missingVersionCodeThrows() {
        try {
            UpdateManifestParser.parse("""{"versionName":"1.0.0"}""")
            fail("缺少 versionCode 时应抛异常，好让上层切换下一个镜像")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("versionCode"))
        }
    }

    @Test
    fun missingVersionNameThrows() {
        try {
            UpdateManifestParser.parse("""{"versionCode":1}""")
            fail("缺少 versionName 时应抛异常")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("versionName"))
        }
    }

    @Test
    fun htmlErrorPageThrows() {
        // 加速镜像挂掉时常返回 HTML，解析必须失败而不是静默产出空清单
        try {
            UpdateManifestParser.parse("<!DOCTYPE html><html><body>502</body></html>")
            fail("HTML 不应被当成清单解析成功")
        } catch (expected: Exception) {
            assertNotNull(expected)
        }
    }

    // ------------------------------------------------------------ 可安装文件挑选

    @Test
    fun pickAssetPrefersAndroidPlatform() {
        val m = UpdateManifestParser.parse(
            """
            {"versionName":"1.0.0","versionCode":1,"assets":[
              {"type":"apk","platform":"ios","url":"https://example.com/a.ipa"},
              {"type":"universal-apk","platform":"android","url":"https://example.com/a.apk"}
            ]}
            """.trimIndent()
        )
        assertEquals("https://example.com/a.apk", UpdateManifestParser.pickAsset(m)?.url)
    }

    @Test
    fun pickAssetAcceptsAssetWithoutPlatform() {
        val m = UpdateManifestParser.parse(
            """
            {"versionName":"1.0.0","versionCode":1,"assets":[
              {"type":"apk","url":"https://example.com/a.apk"}
            ]}
            """.trimIndent()
        )
        assertEquals("https://example.com/a.apk", UpdateManifestParser.pickAsset(m)?.url)
    }

    @Test
    fun pickAssetIgnoresUnsupportedTypes() {
        val m = UpdateManifestParser.parse(
            """
            {"versionName":"1.0.0","versionCode":1,"assets":[
              {"type":"ipa","url":"https://example.com/a.ipa"}
            ]}
            """.trimIndent()
        )
        assertNull(UpdateManifestParser.pickAsset(m))
    }

    // ------------------------------------------------------------ changelog 地址

    @Test
    fun resolvesChangelogUrlNextToManifest() {
        val url = UpdateManifestParser.resolveChangelogUrl(
            "https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/stable.json",
            7L,
        )
        assertEquals(
            "https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/changelogs/7.changelog",
            url,
        )
    }

    @Test
    fun resolvesChangelogUrlThroughMirrorAndKeepsQuery() {
        val url = UpdateManifestParser.resolveChangelogUrl(
            "https://gh-proxy.com/https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/stable.json?token=abc",
            3L,
        )
        assertEquals(
            "https://gh-proxy.com/https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/changelogs/3.changelog?token=abc",
            url,
        )
    }

    // ------------------------------------------------------------ 下载地址解析

    private val githubApk =
        "https://github.com/lmc2007/TMallKey/releases/download/v1.0.1/TMKey-1.0.1.apk"

    @Test
    fun proxyModeTriesEveryProxyThenFallsBackToGithub() {
        val urls = resolveDownloadUrls(githubApk, DownloadMode.PROXY)

        assertEquals(UpdateSources.DOWNLOAD_PROXIES.size + 1, urls.size)
        UpdateSources.DOWNLOAD_PROXIES.forEachIndexed { index, proxy ->
            assertEquals(proxy + githubApk, urls[index])
        }
        assertEquals(githubApk, urls.last())
    }

    @Test
    fun directModeUsesGitHubOnly() {
        assertEquals(listOf(githubApk), resolveDownloadUrls(githubApk, DownloadMode.DIRECT))
    }

    @Test
    fun nonGitHubAssetsAreNeverProxied() {
        val gitee = "https://gitee.com/lmc2007/TMallKey/releases/download/v1.0.1/app.apk"
        assertEquals(listOf(gitee), resolveDownloadUrls(gitee, DownloadMode.PROXY))
        assertEquals(listOf(gitee), resolveDownloadUrls(gitee, DownloadMode.DIRECT))
    }

    @Test
    fun blankAssetUrlYieldsNoCandidates() {
        assertTrue(resolveDownloadUrls("   ", DownloadMode.PROXY).isEmpty())
        assertTrue(resolveDownloadUrls("", DownloadMode.DIRECT).isEmpty())
    }

    // ------------------------------------------------------------ 源列表

    @Test
    fun sourcesAreOrderedMirrorsFirstThenGithubDirect() {
        val sources = UpdateSources.all

        assertEquals("GitHub", sources.last().name)
        assertEquals(UpdateSources.MANIFEST_URL, sources.last().manifestUrl)

        sources.dropLast(1).forEach { mirror ->
            assertTrue(
                "镜像源必须是前缀代理格式: ${mirror.manifestUrl}",
                mirror.manifestUrl.endsWith("/${UpdateSources.MANIFEST_URL}"),
            )
            assertEquals(UpdateSources.PROXY_TIMEOUT_MILLIS, mirror.timeoutMillis)
        }
        assertEquals(UpdateSources.GITHUB_TIMEOUT_MILLIS, sources.last().timeoutMillis)
        assertTrue("直连超时应当比代理宽松", sources.last().timeoutMillis > sources.first().timeoutMillis)
    }
}
