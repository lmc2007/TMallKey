package com.fktm.app.vm

import com.fktm.app.water.MtopError
import com.fktm.app.water.WaterDevice

/** Called on the main dispatcher. Keeps the selected device and order together. */
internal class WaterQuickUse {
    data class Active(val device: WaterDevice, val orderId: String, val accountId: String) {
        fun canRecoverWith(candidate: String): Boolean = accountId.isNotBlank() && accountId == candidate
    }

    companion object {
        fun unlockUnconfirmed(error: Exception): Boolean = error !is MtopError || error.code == "NETWORK_ERROR"
    }

    var active: Active? = null
        private set
    private var running = false

    suspend fun start(
        resNo: String,
        query: suspend (String) -> WaterDevice,
        unlock: suspend (String) -> String,
        accountId: String = "",
    ): Active? {
        if (running || active != null) return null
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
            return Active(device, unlock(resNo), accountId).also { active = it }
        } finally {
            running = false
        }
    }

    suspend fun stop(end: suspend (resNo: String, orderId: String) -> Unit) {
        val order = active ?: return
        if (running) return
        running = true
        try {
            end(order.device.resNo, order.orderId)
            active = null
        } finally {
            running = false
        }
    }

    fun confirmPhysicalEnd() {
        if (!running) active = null
    }
}
