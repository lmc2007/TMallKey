package com.fktm.app.update

import org.json.JSONObject

/**
 * `update/stable.json` 的解析。
 *
 * 用 Android 自带的 `org.json` 而不是 kotlinx.serialization —— 少一个依赖，
 * 而这份清单的字段量完全不值得引入序列化框架。
 */
data class UpdateManifest(
    val channel: String,
    val versionName: String,
    val versionCode: Long,
    val isReady: Boolean,
    val minVersionCode: Long?,
    val releaseNotes: String?,
    val releaseUrl: String?,
    val assets: List<UpdateManifestAsset>,
)

data class UpdateManifestAsset(
    val type: String,
    val url: String,
    val sha256: String?,
    val size: Long?,
    val platform: String?,
)

object UpdateManifestParser {

    /** Android 认的可安装类型。 */
    private const val PLATFORM_KEY = "android"

    /**
     * 解析清单。[text] 不是合法 JSON、或缺少必需字段时抛异常，
     * 由调用方记为该源失败并切换到下一个镜像。
     */
    fun parse(text: String): UpdateManifest {
        val o = JSONObject(text)

        val versionName = o.optString("versionName", "").trim()
        require(versionName.isNotEmpty()) { "清单缺少 versionName" }
        require(o.has("versionCode")) { "清单缺少 versionCode" }

        val assetsJson = o.optJSONArray("assets")
        val assets = buildList {
            for (i in 0 until (assetsJson?.length() ?: 0)) {
                val a = assetsJson?.optJSONObject(i) ?: continue
                val url = a.optString("url", "").trim()
                if (url.isEmpty()) continue
                add(
                    UpdateManifestAsset(
                        type = a.optString("type", "apk").trim().ifEmpty { "apk" },
                        url = url,
                        sha256 = a.optString("sha256", "").trim().ifEmpty { null },
                        size = if (a.has("size")) a.optLong("size").takeIf { it > 0 } else null,
                        platform = a.optString("platform", "").trim().ifEmpty { null },
                    )
                )
            }
        }

        return UpdateManifest(
            channel = o.optString("channel", "stable").trim().ifEmpty { "stable" },
            versionName = versionName,
            versionCode = o.getLong("versionCode"),
            isReady = o.optBoolean("isReady", false),
            minVersionCode = if (o.has("minVersionCode")) o.optLong("minVersionCode") else null,
            releaseNotes = o.optString("releaseNotes", "").takeIf { it.isNotBlank() },
            releaseUrl = o.optString("releaseUrl", "").trim().ifEmpty { null },
            assets = assets,
        )
    }

    /**
     * 更新日志与清单同目录：`update/changelogs/{versionCode}.changelog`。
     * 清单若带 query（镜像偶有透传），query 原样带过去。
     */
    fun resolveChangelogUrl(manifestUrl: String, versionCode: Long): String {
        val base = manifestUrl.substringBefore('?')
        val query = manifestUrl.substringAfter('?', "")
        val parent = base.substringBeforeLast('/', base)
        val url = "$parent/changelogs/$versionCode.changelog"
        return if (query.isEmpty()) url else "$url?$query"
    }

    /** 从清单里挑出适合本平台的可安装文件。 */
    fun pickAsset(manifest: UpdateManifest): UpdateManifestAsset? {
        val supported = setOf("universal-apk", "apk")
        return manifest.assets.firstOrNull { asset ->
            val platformOk = asset.platform == null || asset.platform.equals(PLATFORM_KEY, ignoreCase = true)
            platformOk && asset.type.lowercase() in supported
        } ?: manifest.assets.firstOrNull { it.type.lowercase() in supported }
    }
}
