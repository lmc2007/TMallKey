package com.fktm.app.vm

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fktm.app.water.MtopClient
import com.fktm.app.water.MtopError
import com.fktm.app.water.QrCodec
import com.fktm.app.water.WaterApi
import com.fktm.app.water.WaterDeviceSortMode
import android.content.Intent
import com.fktm.app.water.WaterDiag
import com.fktm.app.water.WaterDevices
import com.fktm.app.water.CookiePuller
import com.fktm.app.water.LockImport
import com.fktm.app.water.WaterSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 饮水页的状态机。
 *
 * 三个阶段：登录 → 认出设备 → 解锁 / 结束。
 * 每个网络动作只做一件事，失败就停在原地并把原因说清楚，
 * 不做「自动重试到成功」—— 真出水的东西宁可少动。
 */
class WaterViewModel : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        /** 顶部一句话状态。 */
        val status: String = "",
        val error: String? = null,
        /** 已有可用登录态。 */
        val loggedIn: Boolean = false,
        /** 没有登录态，需要引导用户去登录。 */
        val needsLogin: Boolean = false,
        /** 登录的淘宝账号（打码后的 munb），用来和天猫校园里那个账号对照。 */
        val account: String = "",
        /** 当前排序下的已保存设备。 */
        val devices: List<WaterDevices.Saved> = emptyList(),
        val sortMode: WaterDeviceSortMode = WaterDeviceSortMode.RECENT,
        val processingResNo: String? = null,
        val deviceError: DeviceError? = null,
        val activeDevice: WaterDevices.Saved? = null,
    )

    data class DeviceError(val resNo: String, val message: String)

    var state by mutableStateOf(UiState())
        private set

    private var api: WaterApi? = null
    private val quickUseFlow = WaterQuickUse()
    private var activeClient: WaterApi? = null
    private var apiAccountId = ""
    private val canRefreshLogin get() = !state.busy && (quickUseFlow.active == null || state.needsLogin)
    private val deviceActionsBlocked get() = state.busy || quickUseFlow.active != null

    /** 进页面时读一次登录态（加密存储里没有就标记 needsLogin）。 */
    fun prepare(context: Context) {
        if (deviceActionsBlocked) return
        reloadDevices(context)
        val jar = runCatching { WaterSession.ensure(context.applicationContext) }.getOrNull()
        if (jar == null) {
            api = null
            state = state.copy(
                busy = false,
                loggedIn = false,
                needsLogin = true,
                status = "",
                error = null,
            )
            return
        }
        installClient(jar)
        state = state.copy(
            busy = false,
            loggedIn = true,
            needsLogin = false,
            status = "登录态就绪（${jar.size} 项）",
            account = WaterSession.maskAccount(WaterSession.accountId(jar)),
            error = null,
        )
        Log.d(TAG, "water: session ready, cookies=${jar.size}")
        WaterDiag.log(context.applicationContext, "prepare", WaterDiag.cookieSummary(jar))
        probeCampusUser(context)
    }

    /**
     * 从本机天猫校园导入登录态（需要一次 root 授权）。
     *
     * 导入后立刻用 `member.user.login` 校验校园身份 —— 这是真正会拒绝普通淘宝会话的那个接口。
     */
    fun importFromDevice(context: Context) {
        if (!canRefreshLogin) return
        viewModelScope.launch {
            state = state.copy(busy = true, status = "正在读取天猫校园的登录态…", error = null)
            try {
                val jar = withContext(Dispatchers.IO) {
                    WaterSession.importFromDevice(context.applicationContext)
                }
                adoptSession(context, jar)
                val account = WaterSession.maskAccount(WaterSession.accountId(jar))
                WaterDiag.log(context.applicationContext, "import", WaterDiag.cookieSummary(jar))
                state = state.copy(
                    busy = false,
                    loggedIn = true,
                    needsLogin = false,
                    account = account,
                    status = "已导入 ${jar.size} 项 cookie，校验校园身份…",
                )
                probeCampusUser(context)
            } catch (e: Throwable) {
                Log.w(TAG, "water: import failed: ${e.message}")
                state = state.copy(
                    busy = false,
                    status = "",
                    needsLogin = true,
                    error = e.message ?: "导入失败",
                )
            }
        }
    }

    /**
     * 回到页面时调用：如果登录态被外部换过（LSPatch 模块广播导入 / root 导入），
     * 就重新加载一次并自检。
     */
    /**
     * 解锁前刷新登录态：从天猫校园进程**拉最新 cookie**，变了就替换。
     *
     * 背景：模块的 socket 里永远是天猫校园**当前**的会话；如果用户在天猫校园里
     * 重新登录过，旧 cookie 会被服务端作废 —— App 里存的旧 jar 就会回
     * `GET_CAMPUS_USER_ERROR`。所以每次扫码/解锁前都拉一次。
     */
    private suspend fun refreshSession(context: Context) {
        val res = withContext(Dispatchers.IO) { CookiePuller.pull() } ?: return
        val jar = res.cookies
        val cur = WaterSession.cached()
        if (cur == jar) return                       // Map.equals 是对称比较
        adoptSession(context, jar)
        WaterDiag.log(context.applicationContext, "pull", WaterDiag.cookieSummary(jar))
        importLocks(context, res)
    }

    /**
     * 门锁自动入库（VOC 静态钥匙 / MV 在线锁）。
     *
     * 不做 CM 校验：钥匙是离线 BLE 数据，会话没绑校园档案时也该先存下来。
     */
    private fun importLocks(context: Context, res: CookiePuller.PullResult) {
        val applied = runCatching { LockImport.apply(context, res.doorlocks, res.mvLocks) }
            .getOrElse {
                Log.d(TAG, "water: lock import failed ${it.javaClass.simpleName}")
                return
            }
        if (!applied.any) return
        WaterDiag.log(context.applicationContext, "locks", applied.toString())
        Log.d(TAG, "water: locks imported $applied")
    }

    fun syncIfImported(context: Context) {
        if (WaterSession.importedAt <= seenImportAt) return
        seenImportAt = WaterSession.importedAt
        prepare(context)
    }

    /**
     * 回到饮水页时调用：**主动**去天猫校园进程拉一次登录态（LSPatch 模块的本地 Socket）。
     *
     * 有登录态就替换并自检；没有（天猫校园没开）就静默跳过。
     * 这条通道不依赖 root，是「免 root」模式的主路径。
     */
    fun syncFromTmall(context: Context) {
        if (!canRefreshLogin) return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { CookiePuller.pull() } ?: return@launch
            if (!canRefreshLogin) return@launch
            // 门锁凭证先入库：离线 BLE 静态数据，不依赖 CM 层，
            // 就算 cookie 会话还没绑校园档案也要收（否则 CM 探测失败会把它一起跳过）
            importLocks(context, res)
            val jar = res.cookies
            val cur = WaterSession.cached()
            // cookie 没变化就不折腾（对称比较，防止一边多一个键漏检）
            if (!state.needsLogin && cur == jar) return@launch
            // ⚠ CM 层探测不过就不覆盖 —— 重新登录后的新会话可能没有校园档案，
            //    拿它去 water.start 会回 GET_CAMPUS_USER_ERROR（实测）
            if (!withContext(Dispatchers.IO) { WaterSession.campusProbeOk(jar) }) {
                WaterDiag.log(context.applicationContext, "pull", "CM 层未绑定，忽略（保留现有会话）")
                return@launch
            }

            if (!canRefreshLogin) return@launch
            val active = quickUseFlow.active
            if (active != null && !active.canRecoverWith(WaterSession.accountId(jar))) return@launch
            adoptSession(context, jar)
            val account = WaterSession.maskAccount(WaterSession.accountId(jar))
            WaterDiag.log(context.applicationContext, "pull", WaterDiag.cookieSummary(jar))
            state = state.copy(
                loggedIn = true,
                needsLogin = false,
                account = account,
                status = "已从天猫校园同步登录态（${jar.size} 项）",
            )
            probeCampusUser(context)
        }
    }

    private var seenImportAt = 0L

    /** CM 会话未激活时置 true：拉起过天猫校园宿舍页，等用户回来重新导入。 */
    private var pendingActivation = false

    /** 登录页返回后调用。 */
    fun onLoggedIn(context: Context, jar: Map<String, String>) {
        if (!canRefreshLogin) return
        try {
            adoptSession(context, jar)
        } catch (e: IllegalStateException) {
            state = state.copy(error = e.message)
            return
        }
        state = state.copy(
            loggedIn = true,
            needsLogin = false,
            status = "校验校园身份…",
            account = WaterSession.maskAccount(WaterSession.accountId(jar)),
            error = null,
        )
        Log.d(TAG, "water: adopted ${jar.size} cookies")
        WaterDiag.log(context.applicationContext, "adopt", WaterDiag.cookieSummary(jar))
        probeCampusUser(context)
    }

    /**
     * 登录后立刻探一次「这个账号在不在天猫校园里」。
     *
     * 淘宝登录成功 ≠ 校园用户存在：实测同一条链路会返回 `GET_CAMPUS_USER_ERROR`，
     * 也就是这个淘宝账号没有校园档案（多半是登进了另一个账号）。
     * 早点说清楚，比让用户在扫码那一步撞墙好。
     */
    private fun probeCampusUser(context: Context) {
        val client = api ?: return
        viewModelScope.launch {
            // CM 层探测：身份只认 `.confong.cn` 的那条 cookie2（2026-11-01 实测）。
            // 导入时若被 `.taobao.com` 的同名 cookie 覆盖，就会回「用户未登录」，water/zmst 全挂。
            val jar = WaterSession.cached() ?: return@launch
            val cmOk = withContext(Dispatchers.IO) { WaterSession.campusProbeOk(jar) }
            if (api !== client || deviceActionsBlocked) return@launch
            WaterDiag.log(context.applicationContext, "cmProbe", if (cmOk) "OK" else "未激活")
            if (!cmOk) {
                val launched = launchTmallDormPage(context.applicationContext)
                pendingActivation = launched
                state = if (launched) {
                    state.copy(
                        status = "缺校园会话：已打开天猫校园的宿舍页，等它加载完回到本页重新导入",
                    )
                } else {
                    state.copy(
                        status = "",
                        error = "缺校园会话：导入的 cookie 里没有 .confong.cn 的 cookie2。" +
                            "请先在天猫校园里打开一次校园页面（如「智能宿舍」），再回来导入。",
                    )
                }
                return@launch
            }
            val err = withContext(Dispatchers.IO) { client.selfCheck() }
            if (api !== client || deviceActionsBlocked) return@launch
            WaterDiag.log(context.applicationContext, "selfCheck", if (err == null) "OK" else err)
            Log.d(TAG, "water: selfCheck -> ${err ?: "OK"}")
            state = if (err == null) {
                state.copy(status = "登录态可用")
            } else {
                state.copy(status = "已导入，但自检没通过", error = err)
            }
        }
    }

    /**
     * 拉起天猫校园的宿舍 H5 页（CampusWebActivity）。
     *
     * 页面加载时 H5 的 UserStore 走 WindVane 调 native mtop，native 会把
     * **带 CM 会话的 cookie2 sync 进 WebView CookieManager** —— 之后再导入就激活了。
     * url/extra 名逆向自 CampusWebActivity 的启动参数（2026-10-30 实测有效）。
     */
    private fun launchTmallDormPage(context: Context): Boolean = runCatching {
        val intent = Intent()
            .setClassName("com.tmall.campus.and", "com.tmall.campus.bizwebview.ui.CampusWebActivity")
            .putExtra(
                "url",
                "https://biz.confong.cn/app/tmall-xiaoyuan/campus-dormitory-m/home" +
                    "?hideNavigator=true&loadType=normal&loginRequired=true&statusBarMode=dark",
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrElse {
        WaterDiag.log(context, "activate", "拉起天猫校园失败: ${it.message}")
        false
    }

    /** 页面 ON_RESUME 时调用：刚拉起过激活页则自动重新导入。 */
    fun resumeAfterActivation(context: Context) {
        if (!pendingActivation) return
        pendingActivation = false
        WaterDiag.log(context.applicationContext, "activate", "回到页面，重新导入")
        importFromDevice(context)
    }

    fun logout(context: Context) {
        if (deviceActionsBlocked) return
        WaterSession.clear(context.applicationContext)
        api = null
        state = UiState(needsLogin = true, devices = state.devices, sortMode = state.sortMode)
    }

    /** 扫码与手动输入共用这个入口，仅录入设备。 */
    fun addDevice(context: Context, input: String) {
        if (deviceActionsBlocked || !state.loggedIn) return
        val scanned = QrCodec.parse(input)
        if (scanned == null) {
            state = state.copy(error = "无法识别饮水机，请检查二维码或设备号")
            return
        }
        state = state.copy(busy = true, status = "正在识别饮水机…", error = null, deviceError = null)
        viewModelScope.launch {
            try {
                val client = clientForAction(context)
                val device = withContext(Dispatchers.IO) { client.deviceInfo(scanned.code) }
                if (WaterDevices.remember(context.applicationContext, device)) {
                    reloadDevices(context)
                    state = state.copy(status = "已添加 ${device.name}")
                } else {
                    state = state.copy(status = "", error = "已保存 30 台饮水机，请在管理设备中移除一台后再试")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(context, "add", e)
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    /** 一次明确点击，校验该设备并解锁。所有设备操作在请求期间保持锁定。 */
    fun quickUse(context: Context, resNo: String) {
        if (deviceActionsBlocked || !state.loggedIn) return
        val saved = state.devices.firstOrNull { it.resNo == resNo } ?: return
        state = state.copy(
            busy = true, status = "检查设备…", error = null,
            processingResNo = resNo, deviceError = null,
        )
        viewModelScope.launch {
            var sentUnlock = false
            try {
                val client = clientForAction(context)
                val active = quickUseFlow.start(
                    resNo,
                    query = { withContext(Dispatchers.IO) { client.deviceInfo(it) } },
                    unlock = {
                        state = state.copy(status = "解锁中…")
                        sentUnlock = true
                        withContext(Dispatchers.IO) { client.start(it).isvOrderId }
                    },
                    accountId = apiAccountId,
                ) ?: return@launch
                activeClient = client
                state = state.copy(
                    activeDevice = saved.copy(name = active.device.name),
                    status = "",
                )
                WaterDevices.touch(context.applicationContext, resNo)
                reloadDevices(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (sentUnlock && WaterQuickUse.unlockUnconfirmed(e)) {
                    "解锁结果未确认，请先查看饮水机状态再试"
                } else explain(e)
                reportFailure(context, "quickUse", e, DeviceError(resNo, message))
            } finally {
                state = state.copy(busy = false, processingResNo = null)
            }
        }
    }

    fun stop(context: Context) {
        if (state.busy || state.needsLogin || quickUseFlow.active == null) return
        val client = activeClient ?: return
        state = state.copy(busy = true, status = "结束中…", error = null)
        viewModelScope.launch {
            try {
                quickUseFlow.stop { resNo, id ->
                    withContext(Dispatchers.IO) { client.stop(resNo, id) }
                }
                activeClient = null
                state = state.copy(activeDevice = null, status = "已结束接水")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(context, "stop", e)
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun confirmPhysicalEnd() {
        if (state.busy || state.error == null || quickUseFlow.active == null) return
        quickUseFlow.confirmPhysicalEnd()
        activeClient = null
        if (state.needsLogin) api = null
        state = state.copy(activeDevice = null, status = "", error = null)
    }

    fun setSortMode(context: Context, mode: WaterDeviceSortMode) {
        if (deviceActionsBlocked) return
        WaterDevices.setSortMode(context.applicationContext, mode)
        reloadDevices(context)
    }

    fun saveCustomOrder(context: Context, resNos: List<String>) {
        if (deviceActionsBlocked) return
        WaterDevices.saveCustomOrder(context.applicationContext, resNos)
        reloadDevices(context)
    }

    fun removeDevice(context: Context, resNo: String) {
        if (deviceActionsBlocked) return
        WaterDevices.remove(context.applicationContext, resNo)
        reloadDevices(context)
        if (state.deviceError?.resNo == resNo) state = state.copy(deviceError = null)
    }

    private fun reloadDevices(context: Context) {
        val appContext = context.applicationContext
        state = state.copy(devices = WaterDevices.list(appContext), sortMode = WaterDevices.sortMode(appContext))
    }

    private fun adoptSession(context: Context, jar: Map<String, String>) {
        val active = quickUseFlow.active
        check(active == null || active.canRecoverWith(WaterSession.accountId(jar))) {
            "请使用开始接水时的账号恢复登录"
        }
        WaterSession.adopt(context.applicationContext, jar)
        installClient(jar)
        if (active != null) activeClient = api
    }

    private fun installClient(jar: Map<String, String>) {
        api = WaterApi(MtopClient(LinkedHashMap(jar)))
        apiAccountId = WaterSession.accountId(jar)
    }

    private suspend fun clientForAction(context: Context): WaterApi {
        try {
            refreshSession(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WaterDiag.log(context.applicationContext, "refresh", e.javaClass.simpleName)
        }
        return api ?: throw MtopError(listOf("FAIL_SYS_SESSION_EXPIRED::请重新登录"))
    }

    private fun reportFailure(context: Context, action: String, error: Exception, deviceError: DeviceError? = null) {
        val expired = error is MtopError && error.code == "FAIL_SYS_SESSION_EXPIRED"
        WaterDiag.log(context.applicationContext, action, if (error is MtopError) error.code else error.javaClass.simpleName)
        if (expired && quickUseFlow.active == null) api = null
        state = state.copy(
            loggedIn = state.loggedIn && !expired,
            needsLogin = state.needsLogin || expired,
            status = "",
            deviceError = deviceError,
            error = when {
                expired && quickUseFlow.active != null -> "登录已过期，请使用原账号恢复登录，然后结束接水"
                expired -> "登录已过期，请重新登录"
                deviceError == null -> explain(error)
                else -> null
            },
        )
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    private fun explain(e: Throwable): String = when (e) {
        is MtopError -> "调用失败：${e.human()}\n(${e.code})"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val TAG = "fkTM"
    }
}
