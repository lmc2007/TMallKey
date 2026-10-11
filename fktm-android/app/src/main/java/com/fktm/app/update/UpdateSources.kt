package com.fktm.app.update

/**
 * 更新源清单。
 *
 * 为什么先走镜像：`raw.githubusercontent.com` 在国内直连经常超时或被重置，
 * 所以按「第三方 GitHub 加速镜像 → GitHub 直连」的顺序逐个尝试，
 * 任何一个能拿到合法清单就停止。
 *
 * 超时策略与 yamibo-app 一致：代理源 5s、GitHub 直连 10s。
 */
object UpdateSources {

    /** 开源仓库主页。「关于」区块的「GitHub 仓库」按钮跳转到这里。 */
    const val REPO_URL = "https://github.com/lmc2007/TMallKey"

    /**
     * 落地页（GitHub Pages）。
     *
     * 「关于」区块的「打开落地页」按钮跳转到这里 —— 页面上有下载入口与完整使用教程，
     * 对普通用户比仓库页友好。仓库主页字段也指向它。
     */
    const val PAGES_URL = "https://lmc2007.github.io/TMallKey/"

    /** 清单地址：仓库主分支 `update/` 目录。改了仓库名或分支要同步这里。 */
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/lmc2007/TMallKey/main/update/stable.json"

    const val PROXY_TIMEOUT_MILLIS = 5_000L
    const val GITHUB_TIMEOUT_MILLIS = 10_000L

    /** 加速镜像前缀。格式为「原 URL 前缀代理」：`https://<host>/<原始 github 地址>`。 */
    private val MIRROR_HOSTS = listOf(
        "github.cnxiaobai.com",
        "gh.halonice.com",
        "ghproxy.sakuramoe.dev",
        "gh.padao.fun",
        "gh.jasonzeng.dev",
        "ghproxy.mirror.skybyte.me",
    )

    /** 有序更新源：6 个加速镜像 + GitHub 直连兜底。顺序即尝试顺序。 */
    val all: List<UpdateSource> = MIRROR_HOSTS.map { host ->
        UpdateSource(
            name = host,
            manifestUrl = "https://$host/$MANIFEST_URL",
            timeoutMillis = PROXY_TIMEOUT_MILLIS,
        )
    } + UpdateSource(
        name = "GitHub",
        manifestUrl = MANIFEST_URL,
        timeoutMillis = GITHUB_TIMEOUT_MILLIS,
    )

    /**
     * APK 下载用的加速代理前缀。
     *
     * 与「检查更新」用的镜像列表是两回事 —— 这里的节点只做下载加速。
     */
    val DOWNLOAD_PROXIES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://gh.dpik.top/",
    )
}
