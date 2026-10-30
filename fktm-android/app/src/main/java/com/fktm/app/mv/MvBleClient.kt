package com.fktm.app.mv

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MV 锁（掌门神通）BLE 客户端 —— 测试版。
 *
 * 协议（逆向自 com.mvlock.supriseble，2026-10-30）：
 *  · Service  0000AFF0-…  Write 0000AFF1-…  Notify 0000AFF2-…
 *  · 开锁 = 把服务端下发的 encryptData（hex）**原样写入 AFF1**，听 AFF2 回包
 *  · 本地零加密：帧由服务端做好，App 只是搬运工（与官方 H5 的 MarvelLockUtils.openLock 一致）
 */
object MvBleClient {

    private val SERVICE_UUID = UUID.fromString("0000aff0-0000-1000-8000-00805f9b34fb")
    private val WRITE_UUID = UUID.fromString("0000aff1-0000-1000-8000-00805f9b34fb")
    private val NOTIFY_UUID = UUID.fromString("0000aff2-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 连接 → 开服务 → 订阅通知 → 写帧 → 收回包 → 断开。
     *
     * @return 锁的回包（hex）。失败抛异常。
     */
    suspend fun unlock(context: Context, mac: String, encryptDataHex: String): String =
        withTimeout(15_000L) {
            suspendCancellableCoroutine { cont ->
                val frame = hexToBytes(encryptDataHex)
                var gatt: BluetoothGatt? = null
                var done = false
                val notify = StringBuilder()

                fun finish(result: Result<String>) {
                    if (done) return
                    done = true
                    handler.post {
                        runCatching { gatt?.disconnect() }
                        handler.postDelayed({ runCatching { gatt?.close() } }, 600)
                    }
                    result
                        .onSuccess { if (cont.isActive) cont.resume(it) }
                        .onFailure { if (cont.isActive) cont.resumeWithException(it) }
                }

                fun writeFrame(g: BluetoothGatt, write: BluetoothGattCharacteristic) {
                    @Suppress("DEPRECATION")
                    write.value = frame
                    write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    if (!g.writeCharacteristic(write)) finish(Result.failure(IllegalStateException("BLE 写入失败")))
                }

                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                g.close()
                                finish(Result.failure(IllegalStateException("连接断开（未收到锁回包）")))
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != 0) {
                            finish(Result.failure(IllegalStateException("服务发现失败 status=$status")))
                            return
                        }
                        val svc = g.getService(SERVICE_UUID)
                        if (svc == null) {
                            finish(Result.failure(IllegalStateException("找不到 AFF0 服务——这可能不是 MV 锁")))
                            return
                        }
                        val write = svc.getCharacteristic(WRITE_UUID)
                        val notifyCh = svc.getCharacteristic(NOTIFY_UUID)
                        if (write == null || notifyCh == null) {
                            finish(Result.failure(IllegalStateException("AFF1/AFF2 特征缺失")))
                            return
                        }
                        g.setCharacteristicNotification(notifyCh, true)
                        val cccd = notifyCh.getDescriptor(CCCD_UUID)
                        if (cccd != null) {
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (!g.writeDescriptor(cccd)) writeFrame(g, write)
                        } else {
                            writeFrame(g, write)
                        }
                    }

                    override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                        if (d.uuid == CCCD_UUID) {
                            val write = d.characteristic.service.getCharacteristic(WRITE_UUID) ?: return
                            writeFrame(g, write)
                        }
                    }

                    private fun collect(value: ByteArray) {
                        notify.append(bytesToHex(value))
                        // 第一包后稍等，收尾包（有些帧回两段）
                        handler.postDelayed({
                            finish(Result.success(notify.toString()))
                        }, 450)
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

                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                if (adapter == null || !adapter.isEnabled) {
                    cont.resumeWithException(IllegalStateException("蓝牙未开启"))
                    return@suspendCancellableCoroutine
                }
                @SuppressLint("MissingPermission")
                val device = adapter.getRemoteDevice(mac)
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

                cont.invokeOnCancellation {
                    done = true
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                }
            }
        }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
        }

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
