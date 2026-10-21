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
import com.fktm.app.water.WaterDevice
import com.fktm.app.water.WaterDiag
import com.fktm.app.water.WaterDevices
import com.fktm.app.water.CookiePuller
import com.fktm.app.credential.CredentialStore
import com.fktm.app.credential.LockCredential
import com.fktm.app.water.WaterSession
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
        /** 扫过的设备，最近使用的排最前。 */
        val devices: List<WaterDevices.Saved> = emptyList(),
        val device: WaterDevice? = null,
        /** 解锁成功后的订单号（有就说明服务端已建单）。 */
        val orderId: String = "",
        val unlockedAt: Long = 0L,
    )

    var state by mutableStateOf(UiState())
        private set

    private var api: WaterApi? = null

    /** 进页面时读一次登录态（加密存储里没有就标记 needsLogin）。 */
    fun prepare(context: Context) {
        if (state.busy) return
        state = state.copy(devices = WaterDevices.list(context.applicationContext))
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
        api = WaterApi(MtopClient(LinkedHashMap(jar)))
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
        if (state.busy) return
        viewModelScope.launch {
            state = state.copy(busy = true, status = "正在读取天猫校园的登录态…", error = null)
            try {
                val jar = withContext(Dispatchers.IO) {
                    WaterSession.importFromDevice(context.applicationContext)
                }
                WaterSession.adopt(context.applicationContext, jar)
                api = WaterApi(MtopClient(LinkedHashMap(jar)))
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
        WaterSession.adopt(context.applicationContext, jar)
        api = WaterApi(MtopClient(LinkedHashMap(jar)))
        WaterDiag.log(context.applicationContext, "pull", WaterDiag.cookieSummary(jar))
        importDoorLocks(context, res.doorlocks)
    }

    /** 门锁凭证入库：离线 BLE 静态数据，不做 CM 校验（按 mac 去重）。 */
    private fun importDoorLocks(context: Context, doorlocks: List<CookiePuller.DoorLock>) {
        doorlocks.forEach { dl ->
            CredentialStore(context.applicationContext).upsert(
                LockCredential(
                    name = "门锁 ${dl.mac.takeLast(5)}",
                    address = dl.mac,
                    activationKey = dl.activationKey,
                )
            )
            WaterDiag.log(context.applicationContext, "doorlock", dl.mac)
            Log.d(TAG, "water: doorlock imported ${dl.mac}")
        }
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
        if (state.busy) return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { CookiePuller.pull() } ?: return@launch
            // 门锁凭证先入库：离线 BLE 静态数据，不依赖 CM 层，
            // 就算 cookie 会话还没绑校园档案也要收（否则 CM 探测失败会把它一起跳过）
            importDoorLocks(context, res.doorlocks)
            val jar = res.cookies
            val cur = WaterSession.cached()
            // cookie 没变化就不折腾（对称比较，防止一边多一个键漏检）
            if (cur != null && cur == jar) return@launch
            // ⚠ CM 层探测不过就不覆盖 —— 重新登录后的新会话可能没有校园档案，
            //    拿它去 water.start 会回 GET_CAMPUS_USER_ERROR（实测）
            if (!withContext(Dispatchers.IO) { WaterSession.campusProbeOk(jar) }) {
                WaterDiag.log(context.applicationContext, "pull", "CM 层未绑定，忽略（保留现有会话）")
                return@launch
            }

            WaterSession.adopt(context.applicationContext, jar)
            api = WaterApi(MtopClient(LinkedHashMap(jar)))
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
    private var appScope: Context? = null

    /** 登录页返回后调用。 */
    fun onLoggedIn(context: Context, jar: Map<String, String>) {
        WaterSession.adopt(context.applicationContext, jar)
        api = WaterApi(MtopClient(LinkedHashMap(jar)))
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
            val err = withContext(Dispatchers.IO) { client.selfCheck() }
            WaterDiag.log(context.applicationContext, "selfCheck", if (err == null) "OK" else err)
            Log.d(TAG, "water: selfCheck -> ${err ?: "OK"}")
            state = if (err == null) {
                state.copy(status = "登录态可用，可以扫码")
            } else {
                state.copy(status = "已导入，但自检没通过", error = err)
            }
        }
    }

    fun logout(context: Context) {
        WaterSession.clear(context.applicationContext)
        api = null
        // 清除的是登录态，不是设备历史 —— 设备列表保留
        state = UiState(needsLogin = true, devices = state.devices)
    }

    /** 扫码 / 手输之后：认出设备。 */
    fun resolve(context: Context, text: String) {
        if (state.busy) return
        val scanned = QrCodec.parse(text)
        if (scanned == null) {
            state = state.copy(error = "这不是饮水机二维码：$text")
            return
        }
        viewModelScope.launch {
            state = state.copy(busy = true, status = "识别设备…", error = null, device = null, orderId = "")
            runCatching { refreshSession(context) }      // 免 root：拉最新登录态
            val client = api ?: runCatching {
                val jar = WaterSession.ensure(context.applicationContext)
                WaterApi(MtopClient(LinkedHashMap(jar))).also { api = it }
            }.getOrElse {
                state = state.copy(busy = false, status = "", needsLogin = true, error = null)
                return@launch
            }
            try {
                val device = withContext(Dispatchers.IO) { client.deviceInfo(scanned.code) }
                // 扫到就存起来，最近用的自动排最前
                WaterDevices.remember(context.applicationContext, device)
                state = state.copy(
                    busy = false,
                    loggedIn = true,
                    status = if (device.unlockable) "设备在线，可以解锁" else "设备当前不可用",
                    device = device,
                    devices = WaterDevices.list(context.applicationContext),
                )
            } catch (e: Throwable) {
                val expired = e is MtopError && e.code == "FAIL_SYS_SESSION_EXPIRED"
                WaterDiag.log(
                    context.applicationContext, "resolve",
                    "${e.javaClass.simpleName} " + (e as? MtopError)?.let { "${it.code} ${it.human()}" } ?: (e.message ?: ""),
                )
                state = state.copy(
                    busy = false,
                    status = "",
                    needsLogin = expired,
                    error = if (expired) "登录态已过期，请重新登录" else explain(e),
                )
            }
        }
    }

    /** 解锁（服务端建单）。 */
    fun unlock(context: Context) {
        val d = state.device ?: return
        val client = api ?: return
        if (state.busy) return
        viewModelScope.launch {
            state = state.copy(busy = true, status = "解锁中…", error = null)
            runCatching { refreshSession(context) }      // 免 root：拉最新登录态
            val live = api ?: client
            try {
                val res = withContext(Dispatchers.IO) { live.start(d.resNo) }
                Log.d(TAG, "water: start ok, order=${res.isvOrderId}")
                WaterDevices.touch(context.applicationContext, d.resNo)
                state = state.copy(
                    busy = false,
                    status = "已解锁 · 请在机器上按出水键",
                    orderId = res.isvOrderId,
                    unlockedAt = System.currentTimeMillis(),
                    devices = WaterDevices.list(context.applicationContext),
                )
            } catch (e: Throwable) {
                Log.w(TAG, "water: start failed: ${e.message}")
                WaterDiag.log(
                    context.applicationContext, "unlock",
                    (e as? MtopError)?.let { "${it.code} ${it.human()}" } ?: (e.message ?: e.javaClass.simpleName),
                )
                state = state.copy(busy = false, status = "", error = explain(e))
            }
        }
    }

    /** 结束接水 / 结单。 */
    fun stop() {
        val d = state.device ?: return
        val client = api ?: return
        if (state.busy) return
        viewModelScope.launch {
            state = state.copy(busy = true, status = "结束中…", error = null)
            try {
                withContext(Dispatchers.IO) { client.stop(d.resNo, state.orderId) }
                state = state.copy(busy = false, status = "已结束", orderId = "", unlockedAt = 0L)
            } catch (e: Throwable) {
                state = state.copy(busy = false, status = "", error = explain(e))
            }
        }
    }

    /** 从列表里删除一台设备（只删本地记录）。 */
    fun removeDevice(context: Context, resNo: String) {
        WaterDevices.remove(context.applicationContext, resNo)
        state = state.copy(
            devices = WaterDevices.list(context.applicationContext),
            device = state.device?.takeIf { it.resNo != resNo },
        )
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    fun clearDevice() {
        state = state.copy(device = null, orderId = "", unlockedAt = 0L, status = "")
    }

    private fun explain(e: Throwable): String = when (e) {
        is MtopError -> "调用失败：${e.human()}\n(${e.code})"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val TAG = "fkTM"
    }
}
