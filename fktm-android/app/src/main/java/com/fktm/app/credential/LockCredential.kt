package com.fktm.app.credential

import com.fktm.app.protocol.VocProtocol

/**
 * 一把门锁的凭证。
 *
 * ⚠️ [activationKey] 就是**钥匙**：拿到它即可永久离线开锁。
 * 落盘时必须经 [CredentialStore] 用 Android Keystore 加密，绝不明文存储、绝不入云。
 */
data class LockCredential(
    val name: String,
    /** 门锁 BLE 地址，形如 `AA:BB:CC:DD:EE:FF` */
    val address: String,
    /** 32 hex，例如 `DEADBEEF...`；MV 锁没有静态钥匙，此字段为空串 */
    val activationKey: String,
    val serviceUuid: String = VocProtocol.SERVICE_UUID,
    val deviceName: String = VocProtocol.VOC_LOCK_TAG,
    /** `"voc"`（静态钥匙离线开锁）或 `"mv"`（掌门神通，在线取帧开锁） */
    val type: String = TYPE_VOC,
) {
    companion object {
        const val TYPE_VOC = "voc"
        const val TYPE_MV = "mv"
    }

    val isMv: Boolean get() = type == TYPE_MV

    /** 用于展示的脱敏 key（只露头尾各 4 位）。 */
    fun maskedKey(): String =
        if (isMv) "在线模式"
        else if (activationKey.length <= 8) "****"
        else "${activationKey.take(4)}…${activationKey.takeLast(4)}"
}
