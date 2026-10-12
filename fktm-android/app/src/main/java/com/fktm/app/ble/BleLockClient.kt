package com.fktm.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.fktm.app.protocol.VocProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/** BLE 相关异常（UI 据此给出可读提示）。 */
class BlePermissionException : Exception("缺少蓝牙权限")
class BleDisabledException : Exception("蓝牙未开启")
class BleUnavailableException(message: String) : Exception(message)

/**
 * BleLockClient —— 门锁 BLE 客户端（Android 原生 GATT）
 *
 * 与 `fktm/ble_client.py`（PC/bleak 版，已真机开锁成功）**行为同构**，
 * 只换底层传输。真机实测参数：
 *   Service  = 00002760-08c2-11e1-9073-0e8ac72e1001
 *   write    = 该 Service 下 properties 含 WRITE / WRITE_NO_RESPONSE 的特征（实测 …0001）
 *   notify   = 该 Service 下 properties 含 NOTIFY / INDICATE 的特征（实测 …0002）
 *   CCCD     = 00002902-...（0x2902）
 *   切包     = 20 字节/包，包间 50ms
 *   消息边界 = 最后一个 notify 后空闲 200ms
 *   设备名   = ~vTmallLock
 *
 * 时序：0D 请求随机数 → 13 建立加密链路 → 0B 开锁（content 为空）
 */
class BleLockClient(private val appContext: Context) {

    companion object {
        private const val TAG = "fkTMBle"
        private val CCCD_UUID: UUID = UUID.fromString(VocProtocol.CCCD_UUID)
        private const val WRITE_TIMEOUT_MS = 1500L
        private const val WRITE_RETRY = 4

        /** 长度判定失败时的兜底空闲阈值（原 200ms → 60ms） */
        private const val FALLBACK_IDLE_MS = VocProtocol.MESSAGE_FALLBACK_IDLE_MS

        /** 收帧轮询间隔（长度判定后无需密集轮询） */
        private const val POLL_INTERVAL_MS = 4L

        /** 开锁后连接保活时长：期间再次开锁可直接复用（秒开），超时自动断开让出连接 */
        const val DEFAULT_IDLE_CLOSE_MS = 60_000L

        /**
         * 冷启动预连接窗口。
         *
         * App 一进入前台就先连上门锁（预连接），但**不无限占用**：
         * 窗口内没有任何开锁/读电量动作就自动断开让出连接（省电、也避免占着门锁）。
         * 一旦发生开锁，则改按 [DEFAULT_IDLE_CLOSE_MS] 保活。
         */
        const val COLD_START_HOLD_MS = 30_000L
    }

    private val adapter: BluetoothAdapter?
        get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    /** 连接状态（onConnectionStateChange 维护），供连接复用判断 */
    @Volatile private var connected = false
    private var currentAddress: String? = null

    /** 是否正在建连（预连接 / 现场建连）—— 仅供 UI 展示，不参与逻辑判断。 */
    @Volatile private var connecting = false

    /** 当前是否有一条可用连接（可跳过「连接 + 服务发现」直接收发）。 */
    val isReady: Boolean
        get() = connected && gatt != null && writeChar != null && notifyChar != null

    /** 是否正在建连（UI 用来显示「预连接中…」）。 */
    val isConnecting: Boolean
        get() = connecting

    /** 当前连接的目标地址（null = 未连接）。 */
    val readyAddress: String?
        get() = if (isReady) currentAddress else null

    private val writeType: Int
        get() = if (writeChar?.properties?.and(BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

    private val rxBuf = ByteArrayOutputStream()
    private val rxLock = Any()
    @Volatile private var lastRxAt = 0L
    private var writeSignal = CompletableDeferred<Unit>()

    /** 收发时序日志（供 UI 展示 / 排障）。 */
    val log = mutableListOf<String>()
    private var t0 = 0L

    /** 连接保活用的独立作用域（不随 ViewModel 销毁而取消）。 */
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleCloseJob: Job? = null

    // ==================================================================
    // 前台驻留（方案 A）—— 连接只在 App 可见期间保持
    // ==================================================================

    /** MainActivity 的可见状态（onStart / onStop 驱动）。 */
    @Volatile private var appForeground = false

    /** 正在进行的开锁/读电量任务数：>0 时不允许因「转入后台」而掐断连接。 */
    private var inFlight = 0
    private val flightLock = Any()

    /** 建连互斥：预热与开锁不会同时新建连接（避免撞 GATT_BUSY / 互相 close）。 */
    private val connectMutex = Mutex()

    val isAppForeground: Boolean get() = appForeground
    val isBusy: Boolean get() = synchronized(flightLock) { inFlight > 0 }

    /**
     * 由 MainActivity 生命周期驱动：
     * - `true`（onStart）：进入前台，**开启冷启动窗口** ——
     *   预连接门锁，[COLD_START_HOLD_MS] 内没有开锁/读电量就自动断开；
     * - `false`（onStop）：离开前台，**立即让出门锁连接**（有任务在跑则等它收尾）。
     *
     * 息屏同样会走到 onStop，所以「前台驻留」天然被系统息屏兜底，不会无限占用门锁。
     */
    fun setForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        if (foreground) {
            // 冷启动：先连上再说，但只保活一个窗口（没动作就自动让出，见 COLD_START_HOLD_MS）。
            note("app -> foreground, cold-start window ${COLD_START_HOLD_MS}ms")
            scheduleIdleClose(COLD_START_HOLD_MS, "冷启动窗口")
        } else if (!isBusy) {
            note("app -> background, release connection")
            cancelIdleClose()
            close()
        }
        // 有任务在跑时不打断：由 endTask() 收尾
    }

    /** 标记一段「需要连接」的任务开始（开锁 / 读电量），期间不因退到后台被掐断。 */
    fun beginTask() {
        synchronized(flightLock) { inFlight++ }
        cancelIdleClose()
    }

    /** 任务结束：**前台按开锁后保活 60s**（期间再开锁重新计时），**后台立即让出**。 */
    fun endTask() {
        val left = synchronized(flightLock) { if (inFlight > 0) inFlight - 1 else 0 }
        if (left > 0) return
        cancelIdleClose()
        if (appForeground) {
            note("task done -> hold connection ${DEFAULT_IDLE_CLOSE_MS}ms")
            scheduleIdleClose(DEFAULT_IDLE_CLOSE_MS, "开锁后保活")
            return
        }
        if (isReady) note("task done & background -> release connection")
        close()
    }

    private fun note(s: String) {
        val ts = if (t0 == 0L) 0.0 else (SystemClock.elapsedRealtime() - t0) / 1000.0
        val line = "t=%6.3fs  %s".format(ts, s)
        log.add(line)
        Log.d(TAG, line)
    }

    /** 清空时序日志并重置时间基准（每次开锁前调用，UI 只显示本次）。 */
    fun clearLog() {
        log.clear()
        t0 = SystemClock.elapsedRealtime()
    }

    // ==================================================================
    // 连接保活（提速：避免每次开锁都重连）
    // ==================================================================

    /**
     * 空闲 [idleMs] 后自动断开。两个使用场景：
     *  · 冷启动窗口（[COLD_START_HOLD_MS]）—— 进前台先连，没动作就放；
     *  · 开锁后保活（[DEFAULT_IDLE_CLOSE_MS]）—— 开锁完留着，方便秒开第二次。
     *
     * [reason] 只用于时序日志，方便在界面上区分是哪条规则触发的断开。
     *
     * 注意：预连接刚发起时连接**还没建立**（`isReady` 为 false），
     * 所以这里先等它连上（或等到窗口到期）再倒计时 —— 否则会漏判成
     * 「窗口到期时还没连上 → 什么都不做」，导致连上之后一直不断开。
     */
    fun scheduleIdleClose(idleMs: Long = DEFAULT_IDLE_CLOSE_MS, reason: String = "idle") {
        idleCloseJob?.cancel()
        idleCloseJob = keepAliveScope.launch {
            val deadline = SystemClock.elapsedRealtime() + idleMs
            while (!isReady && !isBusy && SystemClock.elapsedRealtime() < deadline) delay(200)
            val rest = deadline - SystemClock.elapsedRealtime()
            if (rest > 0) delay(rest)
            if (isReady && !isBusy) {
                note("$reason ${idleMs}ms 到期 -> auto disconnect")
                close()
            }
        }
    }

    /** 取消空闲断开（有新任务要复用连接时调用）。 */
    fun cancelIdleClose() {
        idleCloseJob?.cancel()
        idleCloseJob = null
    }

    // ==================================================================
    // 权限 / 适配器
    // ==================================================================

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requireReady() {
        if (!hasPermissions()) throw BlePermissionException()
        val a = adapter ?: throw BleUnavailableException("本机无蓝牙适配器")
        if (!a.isEnabled) throw BleDisabledException()
    }

    // ==================================================================
    // 扫描
    // ==================================================================

    data class FoundLock(val address: String, val name: String, val rssi: Int)

    /**
     * 扫描门锁。
     *
     * 两种过滤方式，**优先 [addressFilter]**：
     *  · [addressFilter] != null —— **只认这一把锁的 MAC**，其它设备（哪怕广播名也是
     *    `~vTmallLock`）一律忽略。用于「已知地址、只想去连指定门锁」的场景；
     *  · 否则按 [nameFilter] 匹配广播名 —— 谁先广播返回谁。**仅在地址未知时用。**
     *
     * [timeoutMs] 内未发现返回 null。
     */
    @SuppressLint("MissingPermission")
    suspend fun scan(
        timeoutMs: Long = 8_000,
        nameFilter: String = VocProtocol.VOC_LOCK_TAG,
        addressFilter: String? = null,
    ): FoundLock? {
        requireReady()
        val a = adapter!!
        val found = CompletableDeferred<FoundLock>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val nm = dev.name ?: result.scanRecord?.deviceName ?: ""
                if (addressFilter != null) {
                    // 指定了 MAC 就只认它：名字不作为判据（部分机型扫到的
                    // scanRecord 里没有 deviceName，按名字比反而会漏）。
                    if (!dev.address.equals(addressFilter, ignoreCase = true)) return
                } else if (!nm.contains(nameFilter)) {
                    return
                }
                if (!found.isCompleted) found.complete(FoundLock(dev.address, nm, result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
        }
        val scanner = a.bluetoothLeScanner ?: throw BleUnavailableException("BLE 扫描器不可用")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, cb)
        note("scan start (${timeoutMs}ms)${if (addressFilter != null) " target=$addressFilter" else " name~$nameFilter"}")
        return try {
            withTimeoutOrNull(timeoutMs) { found.await() }
        } finally {
            scanner.stopScan(cb)
            note("scan stop")
        }
    }

    // ==================================================================
    // 连接
    // ==================================================================

    /**
     * 连接门锁 —— **只连指定地址那把锁**。
     *
     * @param address 已知地址（来自已添加的凭证）时直连；为 null 才退回「按广播名扫描」，
     *   这条路径仅用于没有凭证的调试场景，正常业务（预连接 / 开锁 / 读电量）永远带地址。
     *
     * 直连失败时会退一步扫一次，但**过滤条件仍是这个 MAC**：
     * 宿舍楼里不止一把 `~vTmallLock`，按名字随便挑一把既连不对（没 key 也开不了）
     * 又白占别人的连接，所以绝不退化到「谁先广播连谁」。
     *
     * @return 实际连上的地址
     */
    suspend fun connect(
        address: String? = null,
        connectTimeoutMs: Long = 15_000,
        scanTimeoutMs: Long = 8_000,
    ): String {
        requireReady()
        t0 = SystemClock.elapsedRealtime()
        log.clear()

        val target = address
        if (target != null) {
            note("direct connect $target")
            if (connectGatt(target, connectTimeoutMs)) {
                currentAddress = target
                return target
            }
            note("direct connect failed, fallback to MAC-filtered scan")
            close()
            val hit = scan(scanTimeoutMs, addressFilter = target)
                ?: throw BleUnavailableException("未发现门锁 $target（不在范围内或未上电）")
            if (!connectGatt(hit.address, connectTimeoutMs)) {
                throw BleUnavailableException("BLE 连接失败（$target）")
            }
            currentAddress = hit.address
            return hit.address
        }

        // 地址未知：只能按广播名扫（调试用，业务路径不该走这里）
        val found = scan(scanTimeoutMs)
            ?: throw BleUnavailableException("未发现门锁（广播名应含 ${VocProtocol.VOC_LOCK_TAG}）")
        note("found ${found.name} ${found.address} rssi=${found.rssi}")
        if (!connectGatt(found.address, connectTimeoutMs)) throw BleUnavailableException("BLE 连接失败")
        currentAddress = found.address
        return found.address
    }

    /**
     * 确保已连上 [address] —— **提速核心**。
     *
     * - 已有可用连接且地址相同 → **直接复用**，跳过「连接 + 服务发现」（省 1~3s）；
     * - 否则断开重连。
     *
     * 全程持 [connectMutex]，因此「前台预热」与「用户点开锁」不会并发建连。
     * 协程被取消时（通常是预热给开锁让路）会顺手关掉半开的连接，不留悬挂 GATT。
     */
    suspend fun ensureConnected(
        address: String? = null,
        connectTimeoutMs: Long = 15_000,
        scanTimeoutMs: Long = 8_000,
    ): String = connectMutex.withLock {
        try {
            if (address != null && isReady && currentAddress == address) {
                note("reuse live connection (skip connect + service discovery)")
                return@withLock address
            }
            if (isReady) note("existing connection is for ${currentAddress}, reconnecting")
            close()
            connecting = true
            connect(address, connectTimeoutMs, scanTimeoutMs)
        } catch (e: CancellationException) {
            close()
            throw e
        } finally {
            connecting = false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(address: String, timeoutMs: Long): Boolean {
        val device: BluetoothDevice = try {
            adapter!!.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val done = CompletableDeferred<Boolean>()

        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connected = true
                        // 提速：请求高优先级连接（更短 connection interval），
                        // 加快后续服务发现与每一次 GATT 读写往返。
                        try {
                            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            note("connection priority = HIGH")
                        } catch (e: Exception) {
                            note("requestConnectionPriority 失败: ${e.message}")
                        }
                        note("connected; discovering services ...")
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connected = false
                        note("disconnected (status=$status)")
                        if (!done.isCompleted) done.complete(false)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    note("service discovery failed status=$status")
                    if (!done.isCompleted) done.complete(false)
                    return
                }
                val svc = g.getService(UUID.fromString(VocProtocol.SERVICE_UUID))
                if (svc == null) {
                    note("服务未找到: ${g.services.map { it.uuid }}")
                    if (!done.isCompleted) done.complete(false)
                    return
                }
                for (c in svc.characteristics) {
                    val p = c.properties
                    val isWrite = (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                            (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    val isNotify = (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                            (p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    if (writeChar == null && isWrite) writeChar = c
                    if (notifyChar == null && isNotify) notifyChar = c
                }
                if (writeChar == null || notifyChar == null) {
                    note("缺少 write/notify 特征: ${svc.characteristics.map { it.uuid.toString() to it.properties }}")
                    if (!done.isCompleted) done.complete(false)
                    return
                }
                note("write=${writeChar!!.uuid} notify=${notifyChar!!.uuid}")
                enableNotify()
                note("notify enabled (CCCD 已写入)")
                if (!done.isCompleted) done.complete(true)
            }

            // API 33+
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) handleRx(value)
            }

            // API < 33
            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value?.let { handleRx(it) }
                }
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (!writeSignal.isCompleted) writeSignal.complete(Unit)
            }
        }

        gatt = device.connectGatt(appContext, false, cb, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) return false

        val ok = withTimeoutOrNull(timeoutMs) { done.await() } ?: false
        if (!ok) note("connect timeout / failed")
        return ok
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify() {
        val g = gatt ?: return
        val c = notifyChar ?: return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }
    }

    // ==================================================================
    // 收 / 发
    // ==================================================================

    private fun handleRx(data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(rxLock) {
            rxBuf.write(data)
            lastRxAt = SystemClock.elapsedRealtime()
        }
        note("RX  ${VocProtocol.bytesToHex(data).uppercase()}")
    }

    /**
     * 累积 notify，**按帧长度精确判定整帧**（替代 App 的 200ms 空闲等待）。
     *
     * 每个响应的总长度都能从帧头算出来（明文帧 7+len / 密文帧 5+len），
     * 收满即可返回 → 三次收帧合计省约 600ms。
     * 长度判定失败（帧被截断/非本协议数据）时回退到 FALLBACK_IDLE_MS 空闲兜底。
     */
    private suspend fun recvFrame(timeoutMs: Long): String? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            synchronized(rxLock) {
                if (rxBuf.size() > 0) {
                    val bytes = rxBuf.toByteArray()
                    val want = VocProtocol.expectedFrameLength(bytes)
                    if (want != null && bytes.size >= want) {
                        rxBuf.reset()
                        if (bytes.size > want) rxBuf.write(bytes, want, bytes.size - want)
                        return VocProtocol.bytesToHex(bytes.copyOf(want)).uppercase()
                    }
                    if (SystemClock.elapsedRealtime() - lastRxAt >= FALLBACK_IDLE_MS) {
                        rxBuf.reset()
                        return VocProtocol.bytesToHex(bytes).uppercase()
                    }
                }
            }
            if (SystemClock.elapsedRealtime() >= deadline) return null
            delay(POLL_INTERVAL_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun doWrite(packet: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = writeChar ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, packet, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.value = packet
            @Suppress("DEPRECATION")
            c.writeType = writeType
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    /** 按 20 字节切包发送，并等 GATT 写回调（避免 GATT_BUSY）；末包后不再等待。 */
    private suspend fun sendFrame(frameHex: String) {
        val packets = VocProtocol.splitPackets(frameHex)
        for ((idx, packet) in packets.withIndex()) {
            var sent = false
            for (attempt in 1..WRITE_RETRY) {
                writeSignal = CompletableDeferred()
                if (doWrite(packet)) {
                    withTimeoutOrNull(WRITE_TIMEOUT_MS) { writeSignal.await() }
                    note("TX  ${VocProtocol.bytesToHex(packet).uppercase()}")
                    sent = true
                    break
                }
                delay(60)
            }
            if (!sent) throw IOException("GATT 写入失败（门锁可能已被其他设备占用）")
            if (idx < packets.size - 1) delay(VocProtocol.WRITE_DELAY_MS)   // 末包无需再等
        }
    }

    private suspend fun sendAndRecv(frameHex: String, timeoutMs: Long): String? {
        synchronized(rxLock) {
            rxBuf.reset()
            lastRxAt = 0L
        }
        sendFrame(frameHex)
        return recvFrame(timeoutMs)
    }

    // ==================================================================
    // 业务流程（与 ble_client.py / SimulatedLink 同构）
    // ==================================================================

    /**
     * 开锁。返回 (成功?, 说明)。
     * 0D 请求随机数 → 13 建立加密链路 → 0B 开锁（content 为空）
     */
    suspend fun unlock(activationKey: String, timeoutMs: Long = 8_000): Pair<Boolean, String> {
        val t0 = SystemClock.elapsedRealtime()
        var tRnd = t0
        var tEst = t0
        var tUnl = t0
        return try {
            val rndFrame = sendAndRecv(VocProtocol.frameRequestRandomNumber(), timeoutMs)
                ?: return false to "未收到随机数响应"
            tRnd = SystemClock.elapsedRealtime()
            val random = try {
                VocProtocol.fetchRandomNumber(rndFrame)
            } catch (e: Exception) {
                return false to "随机数帧解析失败: ${e.message}（原始 $rndFrame）"
            }
            note("randomNumber=$random")
            // 故意不打印 actualAesKey：它与 randomNumber 同时出现即可反解出 activationKey
            // （key = random XOR actualKey），日志一旦被截图/上传就等于交出了钥匙。

            val estAck = sendAndRecv(VocProtocol.frameEncryptedLinkEstablish(), timeoutMs)
            tEst = SystemClock.elapsedRealtime()
            if (!VocProtocol.isLinkEstablished(estAck ?: "", rndFrame, activationKey)) {
                return false to "建立加密链路失败：activationKey 不匹配"
            }

            val unlockFrame = VocProtocol.frameUnlock(rndFrame, activationKey)
                ?: return false to "开锁帧构造失败"
            val ack = sendAndRecv(unlockFrame, timeoutMs)
                ?: return false to "开锁无响应"
            tUnl = SystemClock.elapsedRealtime()

            if (ack.uppercase().startsWith(VocProtocol.UNLOCK_SUCCESS_ACK)) {
                return true to "开锁成功（裸 ack 命中）"
            }
            val r = VocProtocol.parse(ack, rndFrame, activationKey)
            if (r.ok) true to "开锁成功" else false to "开锁失败：${r.detail ?: "解密失败"}"
        } catch (e: Exception) {
            false to (e.message ?: e.javaClass.simpleName)
        } finally {
            // 分段耗时：定位瓶颈用（0D/13/0B 是纯协议往返）
            note(
                "阶段耗时  0D=${tRnd - t0}ms  13=${tEst - tRnd}ms  0B=${tUnl - tEst}ms  " +
                    "协议合计=${tUnl - t0}ms"
            )
        }
    }

    /** 读电量。返回 (成功?, 百分比, 说明)。 */
    suspend fun battery(activationKey: String, timeoutMs: Long = 8_000): Triple<Boolean, Int?, String> {
        return try {
            val rndFrame = sendAndRecv(VocProtocol.frameRequestRandomNumber(), timeoutMs)
                ?: return Triple(false, null, "未收到随机数响应")
            val estAck = sendAndRecv(VocProtocol.frameEncryptedLinkEstablish(), timeoutMs)
            if (!VocProtocol.isLinkEstablished(estAck ?: "", rndFrame, activationKey)) {
                return Triple(false, null, "建立加密链路失败：activationKey 不匹配")
            }
            val f = VocProtocol.frameBattery(rndFrame, activationKey)
                ?: return Triple(false, null, "电量帧构造失败")
            val ack = sendAndRecv(f, timeoutMs) ?: return Triple(false, null, "电量无响应")
            val r = VocProtocol.parse(ack, rndFrame, activationKey)
            if (!r.ok) return Triple(false, null, "电量查询失败：${r.detail ?: "解密失败"}")
            // 真机实测 content = 00 64 ff 00 → 第 2 字节 0x64 = 100%
            val detail = r.detail!!
            val content = detail.substring(8, minOf(detail.length, 8 + 8))
            val pct = content.substring(2, 4).toIntOrNull(16)
            Triple(true, pct, "电量 ${pct}%")
        } catch (e: Exception) {
            Triple(false, null, e.message ?: e.javaClass.simpleName)
        }
    }

    // ==================================================================
    // 收尾
    // ==================================================================

    @SuppressLint("MissingPermission")
    fun close() {
        try {
            gatt?.let { g ->
                try {
                    g.disconnect()
                } catch (_: Exception) {
                }
                g.close()
            }
        } catch (_: Exception) {
        } finally {
            gatt = null
            writeChar = null
            notifyChar = null
            connected = false
            currentAddress = null
            rxBuf.reset()
            note("closed")
        }
    }
}
