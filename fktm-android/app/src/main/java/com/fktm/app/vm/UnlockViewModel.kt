package com.fktm.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fktm.app.FktmApp
import com.fktm.app.ble.BleDisabledException
import com.fktm.app.ble.BleLockClient
import com.fktm.app.ble.BlePermissionException
import com.fktm.app.ble.BleUnavailableException
import com.fktm.app.credential.LockCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface UnlockState {
    data object Idle : UnlockState
    data object Working : UnlockState
    data class Success(val message: String) : UnlockState
    data class Failure(val message: String) : UnlockState
}

class UnlockViewModel(app: Application) : AndroidViewModel(app) {

    private val fktmApp = app as FktmApp
    private val store = fktmApp.credentialStore

    /** 进程级共享客户端：磁贴/主界面共用一条连接，二次开锁可跳过重连（秒开） */
    private val client = fktmApp.bleClient

    var state by mutableStateOf<UnlockState>(UnlockState.Idle)
        private set
    var locks by mutableStateOf<List<LockCredential>>(emptyList())
        private set
    var logText by mutableStateOf("")
        private set
    var batteryText by mutableStateOf<String?>(null)
        private set

    /** 前台预热任务（App 一可见就悄悄建连）。 */
    private var prewarmJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        locks = store.load()
    }

    /**
     * 预热连接 —— 方案 A 的核心：
     * App 一到前台就把连接建好，用户真点开锁时直接走 0B（无需等 1~2s 建连）。
     *
     * 目标**只有一把**：已添加凭证里的首把（[CredentialStore.primary]）的 MAC。
     * 直接按 MAC 建连，**不发 BLE 扫描**（省电、不要定位权限、也不会误连楼里别的锁）；
     * 万一直连失败，退一步的扫描同样带 MAC 过滤 —— 连不上就报「不在范围内」，
     * 永远不会挑到另一把 `~vTmallLock`。
     *
     * 设计约束：
     *  · 全程静默 —— 不动 state、不弹错、不覆盖日志（失败就当没发生过）；
     *  · 已在开锁/读电量时直接跳过，不抢锁；
     *  · 用户点开锁时**不打断**它，由 `connectMutex` 排队 —— 等预热跑完再复用，
     *    永远不慢于「取消它从头重连」（建连本身就要 1~7s，是最贵的一步）。
     */
    fun prewarm() {
        if (state == UnlockState.Working) return
        // 预热只对 VOC 锁有意义（BLE 建连复用）；MV 锁走在线模式，没有可预热的连接
        val cred = store.load().firstOrNull { !it.isMv } ?: return
        if (client.isReady && client.readyAddress == cred.address) return
        prewarmJob?.cancel()
        prewarmJob = viewModelScope.launch {
            try {
                client.ensureConnected(address = cred.address, connectTimeoutMs = 8_000)
            } catch (_: CancellationException) {
                // 让路给用户操作，正常
            } catch (_: Exception) {
                // 门不在范围内 / 权限未给：静默失败，用户真开锁时再走完整流程
            } finally {
                // 预热期间 App 已退到后台 → 立刻交还连接，不做后台驻留
                if (!client.isAppForeground) client.close()
            }
        }
    }

    /**
     * MV 锁（掌门神通）在线开锁：预连接复用 → 联网取一次性开锁帧 → 写帧。
     *
     * 连接生命周期：开锁成功后保活 10 秒（连开/重试），到点自动断开。
     */
    private var mvHoldJob: Job? = null

    @Volatile
    private var mvBusy = false

    private fun mvUnlock(cred: LockCredential) {
        if (mvBusy) return
        mvBusy = true
        mvHoldJob?.cancel()
        state = UnlockState.Working
        logText = ""
        batteryText = null
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = try {
                // MV 开锁是**在线**的：会话在天猫校园里重新登录后旧 cookie2 会失效，
                // 所以先向模块拉一次最新会话（拿不到就沿用本地缓存，不阻塞开锁）。
                val jar = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    refreshSessionFromModule()
                    // ensure = 内存 → 加密存储：进程被杀后重开也能拿到会话
                    com.fktm.app.water.WaterSession.ensure(getApplication())
                }
                val api = com.fktm.app.mv.MvLockApi(java.util.LinkedHashMap(jar))
                // 预连接已就绪则 0ms 复用；否则现场建连
                com.fktm.app.mv.MvBleClient.ensureConnected(
                    getApplication(), cred.address,
                )
                val data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    api.fetchUnlockData(cred.address)
                }
                val resp = com.fktm.app.mv.MvBleClient.writeFrame(data.encryptData)
                true to "开锁指令已写入${if (resp.isNotBlank()) "，锁回包 $resp" else ""}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.fktm.app.water.WaterSession.NotLoggedIn) {
                false to "还没有登录态：用「root 抓取」一键导入（钥匙 + 登录态）"
            } catch (e: Exception) {
                false to (e.message ?: e.javaClass.simpleName)
            }
            val cost = System.currentTimeMillis() - started
            mvBusy = false
            state = if (result.first) {
                UnlockState.Success("${result.second}（${cost}ms）")
            } else {
                UnlockState.Failure("${result.second}（${cost}ms）")
            }
            if (result.first) {
                // 开锁成功：保活 10 秒再断（期间再点开锁直接复用连接）
                mvHoldJob?.cancel()
                mvHoldJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(10_000)
                    if (!mvBusy) com.fktm.app.mv.MvBleClient.close()
                }
            }
        }
    }

    /**
     * 主界面一回到前台就向模块拉一次：**门锁凭证 + 会话**。
     *
     * 门锁（VOC 的 activationKey / MV 的 MAC）只在天猫校园打开门锁页那一刻才存在，
     * 用户从那边切回来时这里拉一次就自动入库，不需要他再点「添加门锁凭证」。
     *
     * 会话只在 `cookie2` 变了才做联网探测（探测要发 mtop 请求，不能每次 resume 都打）。
     */
    fun syncFromModule() {
        viewModelScope.launch {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.fktm.app.water.CookiePuller.pull()
            } ?: return@launch

            // 每次拉取都留一条：排查「为什么没自动导入」时，先看这条在不在
            com.fktm.app.water.WaterDiag.log(
                getApplication(), "syncPull",
                "cookies=${res.cookies.size} doorlocks=${res.doorlocks.size} mv=${res.mvLocks.size}",
            )
            val applied = runCatching {
                com.fktm.app.water.LockImport.apply(
                    getApplication(), res.doorlocks, res.mvLocks,
                )
            }.getOrNull()
            if (applied?.any == true) {
                refresh()
                com.fktm.app.water.WaterDiag.log(
                    getApplication(), "locks", applied.toString(),
                )
            }

            val cur = com.fktm.app.water.WaterSession.cached()
            if (cur != null && cur["cookie2"] == res.cookies["cookie2"]) return@launch
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.fktm.app.water.WaterSession.campusProbeOk(res.cookies)
            }
            if (ok) com.fktm.app.water.WaterSession.adopt(getApplication(), res.cookies)
        }
    }

    /**
     * 向天猫校园进程（LSPatch 模块的本地 Socket）拉一次最新会话，顺便收门锁。
     *
     * 只在**探测通过**时替换：重新登录后的新会话可能还没绑校园档案，
     * 直接覆盖会让原本能用的旧会话也一起废掉。
     */
    private suspend fun refreshSessionFromModule() {
        val res = com.fktm.app.water.CookiePuller.pull() ?: return
        runCatching {
            if (com.fktm.app.water.WaterSession.campusProbeOk(res.cookies)) {
                com.fktm.app.water.WaterSession.adopt(getApplication(), res.cookies)
            }
        }
        // 门锁是离线数据，不管会话能不能用都先收
        runCatching { com.fktm.app.water.LockImport.apply(getApplication(), res.doorlocks, res.mvLocks) }
        refresh()
    }

    /**
     * MV 预连接：门锁页 ON_RESUME 时调用。
     * 已连上直接返回；未连则建连（连上即订阅通知），20 秒没开锁自动断开。
     */
    private var mvPrewarmJob: Job? = null

    fun mvPrewarm() {
        val mv = store.load().firstOrNull { it.isMv } ?: return
        if (com.fktm.app.mv.MvBleClient.isConnectedTo(mv.address)) {
            // 已连接：重置 20 秒倒计时
            mvPrewarmJob?.cancel()
            mvPrewarmJob = viewModelScope.launch {
                kotlinx.coroutines.delay(20_000)
                if (!mvBusy) com.fktm.app.mv.MvBleClient.close()
            }
            return
        }
        if (mvBusy) return
        mvPrewarmJob?.cancel()
        mvPrewarmJob = viewModelScope.launch {
            try {
                com.fktm.app.mv.MvBleClient.ensureConnected(
                    getApplication(), mv.address, timeoutMs = 6_000L,
                )
            } catch (_: Exception) {
                return@launch   // 锁不在范围/蓝牙没开：静默，用户真开锁时再连
            }
            // 连上起算 20 秒：没开锁就断开
            kotlinx.coroutines.delay(20_000)
            if (!mvBusy) com.fktm.app.mv.MvBleClient.close()
        }
    }

    fun importCredential(cred: LockCredential) {
        store.upsert(cred)
        refresh()
    }

    fun removeCredential(address: String) {
        store.remove(address)
        refresh()
    }

    /** 开锁主流程：后台线程跑 GATT，不阻塞 UI。 */
    fun unlock(cred: LockCredential) {
        if (state == UnlockState.Working) return
        if (cred.isMv) {
            mvUnlock(cred)
            return
        }
        state = UnlockState.Working
        logText = ""
        batteryText = null
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            // 预热若正在建连：**不打断**。连接建立是要花 1~7s 的真活儿，
            // 取消它 = 从头重连，只会更慢；交给 connectMutex 排队等它跑完再复用。
            client.beginTask()
            client.clearLog()
            val result = try {
                // 预热成功 → 直接复用（跳过连接 + 服务发现）；否则现场新建
                client.ensureConnected(address = cred.address)
                client.unlock(cred.activationKey)
            } catch (e: BlePermissionException) {
                false to "缺少蓝牙权限，请授权后重试"
            } catch (e: BleDisabledException) {
                false to "蓝牙未开启，请先打开蓝牙"
            } catch (e: BleUnavailableException) {
                false to (e.message ?: "蓝牙不可用")
            } catch (e: Exception) {
                false to (e.message ?: e.javaClass.simpleName)
            } finally {
                logText = client.log.joinToString("\n")
                // 前台保留连接（连点两次秒开）；后台立即让出（不占着门锁）
                client.endTask()
            }
            val cost = System.currentTimeMillis() - started
            state = if (result.first) {
                UnlockState.Success("${result.second}（${cost}ms）")
            } else {
                UnlockState.Failure("${result.second}（${cost}ms）")
            }
        }
    }

    fun readBattery(cred: LockCredential) {
        if (state == UnlockState.Working) return
        state = UnlockState.Working
        logText = ""
        batteryText = null
        viewModelScope.launch {
            client.beginTask()
            client.clearLog()
            val r = try {
                client.ensureConnected(address = cred.address)
                client.battery(cred.activationKey)
            } catch (e: Exception) {
                Triple(false, null, e.message ?: e.javaClass.simpleName)
            } finally {
                logText = client.log.joinToString("\n")
                client.endTask()
            }
            batteryText = r.third
            state = if (r.first) UnlockState.Success(r.third) else UnlockState.Failure(r.third)
        }
    }

    override fun onCleared() {
        // client 是进程级共享实例，这里**不**主动断开：
        // 连接是否保留由「App 是否在前台」决定（MainActivity.onStop 已交还）。
        prewarmJob?.cancel()
        super.onCleared()
    }
}
