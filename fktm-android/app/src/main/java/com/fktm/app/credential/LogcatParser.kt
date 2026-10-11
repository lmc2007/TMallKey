package com.fktm.app.credential

/**
 * 从 logcat 文本里提取门锁凭证。
 *
 * 天猫校园的 `VocLockHelperV2.unlock()` 自己把钥匙明文打进日志：
 *     `unlock:  macAddress: AA:BB:CC:DD:EE:FF activationKey: DEADB…`
 * 这里只是把当初在电脑上做的事（`adb logcat` + 正则）原样搬进 App，
 * **正则与 `fktm/logparse.py` 的 RE_KEY / RE_MAC 保持一致**，两边解析结果必须相同。
 *
 * 纯函数、无 Android 依赖 —— 所以能被 JVM 单测直接覆盖。
 */
object LogcatParser {

    /** 对应 logparse.py: `activationKey[:=]\s*([0-9a-fA-F]{16,128})`；key 恒为 16 字节 = 32 hex */
    private val RE_KEY = Regex("""activationKey[:=]\s*([0-9a-fA-F]{32})""")

    /** 对应 logparse.py: `macAddress[:=]\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})` */
    private val RE_MAC = Regex("""macAddress[:=]\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})""")

    data class Hit(
        val activationKey: String,
        val macAddress: String?,
        val sourceLine: String,
    ) {
        /** 是否拿到了可直接建凭证的全部信息。 */
        val isComplete: Boolean get() = macAddress != null
    }

    /**
     * 扫描日志，返回**最后一条**命中。
     *
     * 取最后一条的理由：日志里可能留着更早的记录（甚至换锁前的），
     * 最新那条才对应刚刚这次开锁。
     *
     * MAC 优先取「与 key 同一行」的那个；同行为空时退回之前见过的 MAC
     * （有的日志版本会把 mac 和 key 拆到两行打）。
     */
    fun parse(text: String): Hit? {
        var hit: Hit? = null
        var lastMac: String? = null

        for (line in text.lineSequence()) {
            RE_MAC.find(line)?.groupValues?.get(1)?.let { lastMac = it.uppercase() }
            val key = RE_KEY.find(line)?.groupValues?.get(1) ?: continue
            val mac = RE_MAC.find(line)?.groupValues?.get(1)?.uppercase() ?: lastMac
            hit = Hit(key.uppercase(), mac, line.trim())
        }
        return hit
    }
}
