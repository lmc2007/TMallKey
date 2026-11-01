package com.fktm.app.water

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File

/**
 * 在 fkTM 进程里跑天猫校园**自带**的 SecurityGuard，直接产 `x-sign` / `x-mini-wua` / `x-sgext`。
 *
 * ## 为什么必须这样
 *
 * 实测（见 PLAN-reverse 阶段 4.8 / 4.9）：App 网关的 `mtop.taobao.mloginService.smsSend`
 * 只差一个 `x-sgext`，服务端连"内容对不对"都不检查就先放行协议层。
 * 而 `x-sgext` 和 `x-sign`、`x-mini-wua` 是**同一个调用**产出的
 * （mtopsdk 的 `InnerSignImpl.getUnifiedSign` → SG 的 `IUnifiedSecurityComponent.getSecurityFactors`
 * → middletier 的 70102），所以拿到 sgext 就等于把整套签名都拿到手。
 *
 * ## 怎么"借"它的 SG
 *
 * SG 是自包含 SDK：插件是 `lib/arm64/libsgmain.so` 这类**zip 包**（内含 classes.dex），
 * 运行时按 `ApplicationInfo.nativeLibraryDir` 找、按 `sourceDir` 校验签名。
 * 所以只要给 SG 一个**包装过的 Context**：
 *  · `sourceDir` / `getPackageCodePath()` → 天猫校园的 base.apk（签名才校验得过）
 *  · `nativeLibraryDir`                   → 我们复制出来的 lib 目录
 *  · `getFilesDir()` 等                   → fkTM 自己的私有目录（不污染天猫校园）
 *
 * 它就会以为自己在天猫校园进程里，正常初始化。**不需要 root，也不需要装补丁版**。
 *
 * ## 前置数据
 *
 * `filesDir/sg/tmall.apk`（天猫校园官方包）与 `filesDir/sg/libs/`（它的 arm64 原生库）。
 * 首次由用户导入（或 root 复制），之后一直可用。
 */
object SgEngine {

    private const val TAG = "fkTM"

    /** SG 需要我们把包名伪造成天猫校园，不然插件路径/签名都对不上。 */
    private const val FAKE_PKG = "com.tmall.campus.and"

    data class Result(
        val ok: Boolean,
        val stage: String,
        val detail: String,
    )

    private fun log(ctx: Context, line: String) {
        WaterDiag.log(ctx, "sg", line)
    }

    /** 准备目录，返回 (apk, libDir)；缺任何一个就返回 null。 */
    fun locate(ctx: Context): Pair<File, File>? {
        val base = File(ctx.filesDir, "sg")
        val apk = File(base, "tmall.apk")
        val libs = File(base, "libs")
        if (!apk.isFile || apk.length() < 1_000_000) return null
        if (!libs.isDirectory) return null
        return apk to libs
    }

    /**
     * 包装 Context，让 SG 看到的是"天猫校园"。
     *
     * `ApplicationInfo` 必须**深拷贝**：直接改原对象会把 fkTM 自己的路径也改掉。
     */
    private class SgContext(base: Context, private val apk: File, private val libs: File) :
        ContextWrapper(base) {

        private val info: ApplicationInfo = ApplicationInfo(base.applicationInfo).apply {
            // 只改"我是谁"（包名 + APK 路径，SG 靠它做签名校验）；
            // nativeLibraryDir 保持 fkTM 自己的 —— Android 10+ 不允许从可写目录 dlopen，
            // SG 的 so 必须来自 APK 的 lib 目录，而它们已经打进 jniLibs 了。
            sourceDir = apk.absolutePath
            publicSourceDir = apk.absolutePath
            packageName = FAKE_PKG
        }

        override fun getApplicationInfo(): ApplicationInfo = info

        // 关键：SG 内部常常绕一圈拿 applicationContext 再取包名/APK 路径，
        // 如果返回 fkTM 的真实 Application，包名就变回 com.fktm.app，校验立刻失败。
        override fun getApplicationContext(): Context = this

        override fun getPackageName(): String = FAKE_PKG

        override fun getPackageCodePath(): String = apk.absolutePath

        override fun getPackageResourcePath(): String = apk.absolutePath

        override fun getFilesDir(): File = File(super.getFilesDir(), "sgdata").apply { mkdirs() }

        override fun getCacheDir(): File = File(super.getCacheDir(), "sgcache").apply { mkdirs() }

        override fun getDir(name: String, mode: Int): File =
            File(super.getFilesDir(), "sgdata/$name").apply { mkdirs() }
    }

    /**
     * 把 APK 里的所有 `classesN.dex` 读进内存，用 [dalvik.system.InMemoryDexClassLoader] 加载。
     *
     * 天猫校园有 15 个 dex，SG 的框架类分散在其中，必须全部加载。
     */
    private fun inMemoryLoader(apk: File): ClassLoader {
        val buffers = ArrayList<java.nio.ByteBuffer>()
        java.util.zip.ZipFile(apk).use { zf ->
            zf.entries().toList()
                .filter { it.name.endsWith(".dex") }
                .sortedBy { it.name }
                .forEach { e -> buffers += java.nio.ByteBuffer.wrap(zf.getInputStream(e).readBytes()) }
        }
        return dalvik.system.InMemoryDexClassLoader(
            buffers.toTypedArray(), javaClass.classLoader,
        )
    }

    /**
     * 初始化 SG 并产出一次统一签名数据。
     *
     * @param api 要签的接口名（`smsSend` 会把它算进签名）
     * @param data 已序列化的业务参数
     * @return `Result.ok=true` 时 detail 是 `getSecurityFactors` 返回的键值对摘要
     */
    fun produce(ctx: Context, api: String, data: String, appKey: String): Result {
        val (apk, libs) = locate(ctx) ?: return Result(
            false, "locate", "缺少 files/sg/tmall.apk 或 libs/",
        )

        val started = System.currentTimeMillis()
        return try {
            // ⚠ Android 14+ 禁止 DexClassLoader 加载"可写的 dex 文件"
            //   （SecurityException: Writable dex file ... is not allowed），
            //   而 files/ 下的文件永远是 app 可写的。所以走内存加载。
            val loader = inMemoryLoader(apk)
            val sgCtx = SgContext(ctx, apk, libs)

            // 1) SecurityGuardManager.getInstance(context)
            val mgrCls = Class.forName(
                "com.alibaba.wireless.security.open.SecurityGuardManager", true, loader,
            )
            val mgr = mgrCls.getMethod("getInstance", Context::class.java)
                .invoke(null, sgCtx)
                ?: return Result(false, "getInstance", "返回 null")
            log(ctx, "getInstance ok in ${System.currentTimeMillis() - started}ms")

            // middletier 插件的 onPluginLoaded 里起了一条异步初始化链（c.a.a / c.a.b），
            // 太早调 70101 会拿到 2405。真机上给它几秒。
            Thread.sleep(3_000)

            // 2) 统一签名组件（middletier 插件提供）
            val unifiedCls = Class.forName(
                "com.alibaba.wireless.security.open.middletier.IUnifiedSecurityComponent",
                true, loader,
            )
            val comp = mgrCls.getMethod("getInterface", Class::class.java)
                .invoke(mgr, unifiedCls)
                ?: return Result(false, "getInterface", "middletier 未加载（getInterface 返回 null）")
            log(ctx, "unified component = ${comp.javaClass.name}")

            // 3) init({authCode}) —— 对应 middletier 的 70101。
            //    空 authCode 实测回 2405（PC 与真机一致），所以这里把几个候选都试一遍，
            //    哪个能让 70101 过就用哪个。
            val candidates = listOf(
                "", "0",
                "apU98phzUu4DAOxRSTtO69oT",                              // utdid
                "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva",          // deviceId
            )
            var lastErr: Throwable? = null
            var inited = false
            for (ac in candidates) {
                val initMap = HashMap<String, Any>()
                initMap["authCode"] = ac
                val r = runCatching {
                    unifiedCls.getMethod("init", HashMap::class.java).invoke(comp, initMap)
                }
                val label = if (ac.isEmpty()) "<空>" else "${ac.take(10)}(${ac.length})"
                if (r.isSuccess) {
                    log(ctx, "init($label) ok")
                    inited = true
                    break
                }
                val e = r.exceptionOrNull()!!
                val code = runCatching {
                    (e.cause ?: e).javaClass.getMethod("getErrorCode").invoke(e.cause ?: e)
                }.getOrNull()
                log(ctx, "init($label) 失败 code=$code")
                lastErr = e
            }
            if (!inited) {
                log(ctx, "所有 authCode 候选都不行，最后错误=${lastErr?.message}")
            }

            // 4) getSecurityFactors({appkey,data,useWua,...}) —— 对应 70102
            val req = HashMap<String, Any>()
            req["appkey"] = appKey
            req["data"] = data
            req["useWua"] = false
            req["api"] = api
            req["authCode"] = ""
            req["env"] = 0
            @Suppress("UNCHECKED_CAST")
            val out = unifiedCls.getMethod("getSecurityFactors", HashMap::class.java)
                .invoke(comp, req) as? Map<String, String>
                ?: return Result(false, "getSecurityFactors", "返回 null")

            val summary = out.entries.joinToString(" | ") { (k, v) ->
                "$k=${if (v.length > 60) "${v.take(24)}…(${v.length})" else v}"
            }
            log(ctx, "factors ${out.size} 项: $summary")
            Result(out.isNotEmpty(), "getSecurityFactors", summary)
        } catch (t: Throwable) {
            val root = generateSequence(t) { it.cause }.last()
            // SG 的 SecException 靠 getErrorCode() 区分原因，message 常常是空的
            val code = runCatching {
                root.javaClass.getMethod("getErrorCode").invoke(root)
            }.getOrNull()
            val msg = "${root.javaClass.simpleName}(code=$code): ${root.message}"
            log(ctx, "失败 $msg")
            Result(false, "exception", msg)
        }
    }
}
