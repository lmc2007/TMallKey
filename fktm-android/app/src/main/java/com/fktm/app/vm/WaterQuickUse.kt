package com.fktm.app.vm

import com.fktm.app.water.MtopError
import com.fktm.app.water.WaterDevice

/** Serializes device checks and unlock requests on the main dispatcher. */
internal class WaterQuickUse {
    companion object {
        fun unlockUnconfirmed(error: Exception): Boolean = error !is MtopError || error.code == "NETWORK_ERROR"
    }

    private var running = false

    suspend fun start(
        resNo: String,
        query: suspend (String) -> WaterDevice,
        unlock: suspend (String) -> Unit,
    ): Boolean {
        if (running) return false
        running = true
        try {
            val device = query(resNo)
            check(device.resNo == resNo) { "设备编号不匹配，请重新添加这台饮水机" }
            check(device.unlockable) {
                when {
                    !device.online -> "设备离线"
                    !device.inBusinessTime -> "不在营业时段"
                    !device.canUse -> "设备停用"
                    else -> device.faultText.ifBlank { "设备状态异常" }
                }
            }
            unlock(resNo)
            return true
        } finally {
            running = false
        }
    }
}
