package com.fktm.app.mv

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MV 锁（掌门神通）BLE 客户端 —— 有状态版，支持预连接。
 *
 * 协议（逆向自 com.mvlock.supriseble，2026-10-30）：
 *  · Service 0000AFF0 / Write AFF1 / Notify AFF2
 *  · 开锁 = 把服务端下发的 encryptData（hex）原样写入 AFF1，听 AFF2 回包
 *
 * 生命周期（与操作员约定）：
 *  · App 到前台（门锁页 ON_RESUME）→ [ensureConnected] 预连接（连上即订阅通知）
 *  · 20 秒没开锁 → 断开（计时在 UnlockViewModel 里）
 *  · 开锁成功 → 保持 10 秒（方便连开/重试）→ 断开
 *  · [close] 随时可调，任何异常路径都保证连接不悬挂
 */
object MvBleClient {

    private val SERVICE_UUID = UUID.fromString("0000aff0-0000-1000-8000-00805f9b34fb")
    private val WRITE_UUID = UUID.fromString("0000aff1-0000-1000-8000-00805f9b34fb")
    private val NOTIFY_UUID = UUID.fromString("0000aff2-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var writeChar: BluetoothGattCharacteristic? = null

    @Volatile
    var readyMac: String? = null
        private set

    @Volatile
    var isReady: Boolean = false
        private set

    private var connectCont: kotlin.coroutines.Continuation<Unit>? = null
    private var writeCont: kotlin.coroutines.Continuation<String>? = null
    private val notifyBuf = StringBuilder()

    /** 已连接且服务/通知就绪（同一 MAC）→ 直接复用。 */
    fun isConnectedTo(mac: String): Boolean = isReady && readyMac == mac

    /**
     * 预连接/复用：连接 → 发现服务 → 订阅通知。已连上同一把锁则立即返回。
     * 超时抛异常（调用方决定要不要提示）；内部保证失败后连接不悬挂。
     */
    suspend fun ensureConnected(context: Context, mac: String, timeoutMs: Long = 10_000L) {
        if (isConnectedTo(mac)) return
        close()
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                connectCont = cont
                try {
                    val adapter =
                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    if (adapter == null || !adapter.isEnabled) {
                        failConnect("蓝牙未开启")
                        return@suspendCancellableCoroutine
                    }
                    @SuppressLint("MissingPermission")
                    val device = adapter.getRemoteDevice(mac)
                    gatt = device.connectGatt(
                        context, false, callback, BluetoothDevice.TRANSPORT_LE,
                    )
                    cont.invokeOnCancellation { failConnect("已取消") }
                } catch (t: Throwable) {
                    failConnect(t.message ?: t.javaClass.simpleName)
                }
            }
        } ?: run {
            close()
            error("连接超时（锁不在范围内？）")
        }
    }

    /**
     * 向 AFF1 写一帧开锁指令，等 AFF2 回包（第一包后 450ms 收尾，兼容多包）。
     * 需先 [ensureConnected]。5 秒没等到回包返回空串（指令已写入）。
     */
    suspend fun writeFrame(hex: String): String {
        val g = gatt ?: error("未连接")
        if (!isReady) error("连接未就绪")
        val ch = writeChar ?: error("写特征缺失")
        notifyBuf.setLength(0)
        val resp = withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                writeCont = cont
                @Suppress("DEPRECATION")
                ch.value = hexToBytes(hex)
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @SuppressLint("MissingPermission")
                if (!g.writeCharacteristic(ch)) {
                    writeCont = null
                    cont.resumeWithException(IllegalStateException("BLE 写入失败"))
                }
            }
        } ?: ""
        // 稍等可能的第二包，然后收尾
        handler.postDelayed({
            writeCont?.let { c ->
                writeCont = null
                c.resume(notifyBuf.toString())
            }
        }, 450)
        return notifyBuf.toString()
    }

    /** 断开并释放。任何时刻可调。 */
    fun close() {
        handler.post {
            runCatching { gatt?.disconnect() }
            handler.postDelayed({ runCatching { gatt?.close() } }, 400)
            gatt = null
            writeChar = null
            readyMac = null
            isReady = false
            failConnect("已断开")
            failWrite("已断开")
        }
    }

    // ---------------- GATT 回调 ----------------

    private fun failConnect(msg: String) {
        connectCont?.let { c ->
            connectCont = null
            c.resumeWithException(IllegalStateException(msg))
        }
    }

    private fun failWrite(msg: String) {
        writeCont?.let { c ->
            writeCont = null
            c.resumeWithException(IllegalStateException(msg))
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    gatt = null
                    writeChar = null
                    readyMac = null
                    isReady = false
                    failConnect("连接断开")
                    failWrite("连接断开")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != 0) {
                failConnect("服务发现失败 status=$status")
                return
            }
            val svc = g.getService(SERVICE_UUID)
            val write = svc?.getCharacteristic(WRITE_UUID)
            val notify = svc?.getCharacteristic(NOTIFY_UUID)
            if (svc == null || write == null || notify == null) {
                failConnect("AFF0/AFF1/AFF2 缺失 —— 这可能不是 MV 锁")
                return
            }
            writeChar = write
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!g.writeDescriptor(cccd)) {
                    // 有些栈不需要等 CCCD 确认也能收包 —— 直接认为就绪
                    markReady(g)
                }
            } else {
                markReady(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (d.uuid == CCCD_UUID && status == 0) markReady(g)
        }

        private fun markReady(g: BluetoothGatt) {
            readyMac = g.device?.address
            isReady = true
            connectCont?.let { c ->
                connectCont = null
                c.resume(Unit)
            }
        }

        private fun collect(value: ByteArray) {
            notifyBuf.append(bytesToHex(value))
            writeCont?.let { c ->
                writeCont = null
                c.resume(notifyBuf.toString())
            }
        }

        // Android 13+（compileSdk 36 的回调；老版本不会调它）
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == NOTIFY_UUID) collect(value)
        }

        // Android 12 及以下
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val v = characteristic.value ?: return
            if (characteristic.uuid == NOTIFY_UUID) collect(v)
        }
    }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
        }

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
