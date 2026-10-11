package com.fktm.app.protocol

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * VocProtocol —— VOC 门锁 BLE 协议复刻层（P3 / Kotlin 版）
 *
 * 与 `fktm/voc_protocol.py` **逐字节等价**（后者已通过 P0 真机抓包 + P2 真机实开双重验收）。
 * 对应天猫校园反编译产物：
 *   com.tmall.campus.doorlock.bluetooth.BluetoothHelper    (协议编解码核心)
 *   com.tmall.campus.doorlock.bluetooth.VocCommandType     (命令码表)
 *   com.tmall.campus.doorlock.VocStatistics                (常量/工作流)
 *   com.tmall.campus.doorlock.VocLockHelper                (切包/消息重组)
 *   com.tmall.campus.utils.AESUtils                        (AES/ECB/NoPadding)
 *
 * ------------------------------------------------------------------
 * 帧格式（两种，别混）
 * ------------------------------------------------------------------
 * [明文帧] 55AA | FF | cmd(1B) | len(2B,大端) | payload | cksum(1B)
 *          cksum = (本帧 cksum 之前所有字节之和) % 256
 * [密文帧] 55AA | len(2B,大端) | cipher | cksum(1B)
 *          cipher = AES-128/ECB/NoPadding( FF|sign|len|content|cksum 补零到 32hex, actualAesKey )
 *
 * 真机实测帧型（2026-10-11 逐字节验证）：
 *   0D 请求随机数 → 明文固定帧 55AAFF0D00000B   响应=明文(含 16B 随机数)
 *   13 建立加密链路 → 明文固定帧 55AAFF13000011  响应=密文
 *   0B 开锁        → 密文帧(content 空)          响应=密文/裸 ack
 *   01 电量        → 密文帧(content 空)          响应=密文
 *
 * ------------------------------------------------------------------
 * 密钥派生（challenge-response）
 * ------------------------------------------------------------------
 *   randomNumber  = 0D 响应的 [14:46]（32 hex）
 *   actualAesKey  = randomNumber XOR activationKey 的后 16 字节
 *   开锁 content 为空 → 拿到 activationKey 即可永久离线开锁
 */
object VocProtocol {

    // ---------------- 常量 ----------------

    const val PROTOCOL_HEADER = "55AA"

    /** ASCII "VOC_BleSmartLock"，仅激活阶段使用 */
    const val DEFAULT_AES_KEY = "564F435F426C65536D6172744C6F636B"

    /** 开锁成功裸 ack */
    const val UNLOCK_SUCCESS_ACK = "55AAFF0B0001000A"

    const val VOC_LOCK_TAG = "~vTmallLock"
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    const val SERVICE_UUID = "00002760-08c2-11e1-9073-0e8ac72e1001"

    /** 每次 writeCharacteristic 最大字节数 */
    const val WRITE_CHUNK = 20

    /** 包间隔 ms（原 50ms；写回调已确认送达，无需再长等） */
    const val WRITE_DELAY_MS = 20L

    /** App 原始做法：消息边界空闲阈值（仅作参考） */
    const val MESSAGE_READ_INTERVAL_MS = 200L

    /** 提速：长度判定失败时的兜底空闲阈值（远小于 200ms） */
    const val MESSAGE_FALLBACK_IDLE_MS = 60L

    const val DEFAULT_SESSION_TIMEOUT_MS = 10_000L

    // ---------------- 命令码 (sign) ----------------

    const val CMD_REQUEST_RANDOM_NUMBER = "0D"
    const val CMD_ENCRYPTED_LINK_ESTABLISH = "13"
    const val CMD_BATTERY_LEVEL = "01"
    const val CMD_UNLOCK = "0B"
    const val CMD_GENERATE_KEY = "0E"
    const val CMD_ACTIVATE = "06"
    const val CMD_RESET = "0C"

    /** 固定明文帧（直接按字节下发，不经 AES） */
    val FIXED_FRAMES: Map<String, String> = mapOf(
        CMD_REQUEST_RANDOM_NUMBER to "55AAFF0D00000B",
        CMD_ENCRYPTED_LINK_ESTABLISH to "55AAFF13000011",
        CMD_BATTERY_LEVEL to "55AAFF010000FF",
        CMD_RESET to "55AAFF0C00000A",
    )

    /**
     * 命令码 → 上行帧型（P0 真机校正）
     * true  = 明文固定帧（直接下发 FIXED_FRAMES[cmd]）
     * false = 密文帧（build(cmd, "", random, key) 现场生成）
     */
    val CMD_IS_CLEAR_FRAME: Map<String, Boolean> = mapOf(
        CMD_REQUEST_RANDOM_NUMBER to true,
        CMD_ENCRYPTED_LINK_ESTABLISH to true,
        CMD_BATTERY_LEVEL to false,
        CMD_UNLOCK to false,
        CMD_GENERATE_KEY to false,
        CMD_ACTIVATE to false,
        CMD_RESET to true,      // 未在真机验证；调试严禁下发（会作废 activationKey）
    )

    // ---------------- 错误码 (BluetoothHelper.CODE_MAP) ----------------

    const val CODE_OK = "00"

    val CODE_MAP: Map<String, String> = mapOf(
        "01" to "格式错误",
        "02" to "超时",
        "03" to "ID已存在",
        "04" to "添加失败",
        "05" to "删除失败",
        "06" to "修改失败",
        "07" to "ID已满",
        "08" to "手动取消",
        "09" to "等待",
        "0a" to "ID不存在",
        "0b" to "门锁未进入待激活状态",
        "0c" to "系统锁定中",
        "0d" to "请先关闭安全模式",
        "0e" to "请先开启所有开门方式",
        "0f" to "超出规定绑定时间",
        "10" to "超出规定时间内的绑定次数",
        "11" to "密码过于简单",
        "12" to "锁体信息查询失败",
        "13" to "锁体配置失败",
        "14" to "锁体不适配",
    )

    private val RANDOM_FRAME_RE = Regex(
        "^55AA[0-9A-Fa-f]{2}0D0011[0-9A-Fa-f]{2}[0-9A-Fa-f]{32}[0-9A-Fa-f]{2}$"
    )

    // ==================================================================
    // hex 工具
    // ==================================================================

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex 长度必须为偶数: $hex" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "非法 hex 字符: $hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** 小写 hex（与 Java/Python 行为一致） */
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_LOWER[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX_LOWER[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_LOWER = "0123456789abcdef".toCharArray()

    // ==================================================================
    // 基础算法
    // ==================================================================

    /** BluetoothHelper.computeValidateCode — 字节和 % 256，输出 2 位小写 hex。 */
    fun computeValidateCode(content: String): String {
        var total = 0
        var i = 0
        while (i < content.length) {
            total += content.substring(i, i + 2).toInt(16)
            i += 2
        }
        return "%02x".format(total % 256)
    }

    /** BluetoothHelper.xor — content 逐字节 XOR key 的末 16 字节（循环）。 */
    fun xor(content: String, hexString: String): String {
        val key = hexString.substring(hexString.length - 32)
        val sb = StringBuilder(content.length)
        val n = content.length / 2
        for (i in 0 until n) {
            val b = content.substring(i * 2, i * 2 + 2).toInt(16)
            val kOff = (i % 16) * 2
            val k = key.substring(kOff, kOff + 2).toInt(16)
            sb.append("%02x".format(b xor k))
        }
        return sb.toString()
    }

    /** BluetoothHelper.fetchRandomNumber — 从 0D 响应帧中取出 16 字节随机数（32 hex）。 */
    fun fetchRandomNumber(content: String): String {
        val upper = content.uppercase()
        if (!RANDOM_FRAME_RE.matches(upper)) throw IllegalArgumentException("随机数不匹配")
        return upper.substring(14, 46)
    }

    /** BluetoothHelper.formatMacAddress — 12 hex 字节逆序后按 ':' 拼接。 */
    fun formatMacAddress(mac12: String?): String? {
        if (mac12 == null || mac12.length != 12) return null
        val parts = ArrayList<String>(6)
        var i = mac12.length - 2
        while (i >= 0) {
            parts.add(mac12.substring(i, i + 2))
            i -= 2
        }
        return parts.joinToString(":")
    }

    /** VocLockHelper.findDevices 的广播解析：首个 'FF' 后偏移 4 起取 12 hex，再逆序。 */
    fun extractMacFromBroadcast(broadcastHex: String): String? {
        val upper = broadcastHex.uppercase()
        val idx = upper.indexOf("FF")
        if (idx < 0) return null
        val start = idx + 4
        if (start + 12 > upper.length) return null
        return formatMacAddress(upper.substring(start, start + 12))
    }

    // ==================================================================
    // AES-128/ECB/NoPadding
    // ==================================================================

    private fun aesKeyBytes(keyHex: String): ByteArray {
        val kb = hexToBytes(keyHex)
        require(kb.size >= 16) { "AES key 至少需要 16 字节" }
        return kb.copyOf(16)      // AESUtils 用前 16 字节
    }

    fun aesEncryptHex(plaintextHex: String, keyHex: String): String {
        val pt = hexToBytes(plaintextHex)
        require(pt.size % 16 == 0) { "NoPadding 要求明文长度为 16 的整数倍，当前 ${pt.size}" }
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKeyBytes(keyHex), "AES"))
        return bytesToHex(c.doFinal(pt))
    }

    fun aesDecryptHex(cipherHex: String, keyHex: String): String? {
        val ct = hexToBytes(cipherHex)
        if (ct.isEmpty() || ct.size % 16 != 0) return null
        return try {
            val c = Cipher.getInstance("AES/ECB/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes(keyHex), "AES"))
            bytesToHex(c.doFinal(ct))
        } catch (_: Exception) {
            null
        }
    }

    // ==================================================================
    // 明文 blob 组装
    // ==================================================================

    /** FF | sign | contentLen(2B) | content | cksum → 补 '0' 对齐 32 hex。 */
    fun assembleDistributeContent(sign: String, content: String): String {
        var core = "FF" + sign + "%04x".format(content.length / 2) + content
        core += computeValidateCode(PROTOCOL_HEADER + core)
        val pad = (32 - core.length % 32) % 32
        return core + "0".repeat(pad)
    }

    // ==================================================================
    // 组帧 / 解帧
    // ==================================================================

    /**
     * 密文帧组装。对应 BluetoothHelper.build(sign, content, randomNumberProtocol, aesKey)。
     * @param randomNumberProtocol 完整的 0D 响应帧 hex（不是裸随机数）
     * @param aesKey activationKey（开锁/电量）或 DEFAULT_AES_KEY（激活阶段）
     */
    fun build(sign: String, content: String, randomNumberProtocol: String?, aesKey: String): String? {
        if (randomNumberProtocol.isNullOrEmpty()) return null
        return try {
            val actualKey = xor(fetchRandomNumber(randomNumberProtocol), aesKey)
            val cipher = aesEncryptHex(assembleDistributeContent(sign, content), actualKey)
            val head = PROTOCOL_HEADER + "%04x".format(cipher.length / 2) + cipher
            (head + computeValidateCode(head)).uppercase()
        } catch (_: Exception) {
            null
        }
    }

    /** BluetoothHelper.extractMessage — 从密文帧中取 cipher。 */
    fun extractMessage(content: String): String = try {
        val upper = content.uppercase()
        val n = upper.substring(4, 8).toInt(16)
        upper.substring(8, 8 + n * 2)
    } catch (_: Exception) {
        ""
    }

    /** 解析结果 */
    data class ParseResult(val ok: Boolean, val detail: String?)

    /**
     * 解帧并校验。对应 BluetoothHelper.parse(...)。
     * 成功 → (true, 解密后全文 hex)；失败 → (false, 错误描述/错误码文案)
     */
    fun parse(contentProtocol: String, randomNumberProtocol: String?, aesKey: String?): ParseResult {
        if (randomNumberProtocol.isNullOrEmpty() || aesKey.isNullOrEmpty()) return ParseResult(false, null)
        return try {
            val actualKey = xor(fetchRandomNumber(randomNumberProtocol), aesKey)
            val plain = aesDecryptHex(extractMessage(contentProtocol), actualKey)
                ?: return ParseResult(false, null)
            if (!plain.startsWith("ff")) return ParseResult(false, "密钥丢失,请联系厂家或经销商")
            val status = plain.substring(8, 10)
            if (status != CODE_OK) {
                return ParseResult(false, CODE_MAP[status] ?: "未知状态码 $status")
            }
            ParseResult(true, plain)
        } catch (e: Exception) {
            ParseResult(false, e.message)
        }
    }

    fun getErrorCode(ack: String): String = ack.substring(8, 10)

    /** VocLockHelper.isLinkEstablished（开锁流程里 key 就是 activationKey）。 */
    fun isLinkEstablished(establishAckHex: String, randomNumberProtocol: String, activationKey: String): Boolean =
        parse(establishAckHex, randomNumberProtocol, activationKey).ok

    // ==================================================================
    // 工作流帧构造器
    // ==================================================================

    fun frameRequestRandomNumber(): String = FIXED_FRAMES.getValue(CMD_REQUEST_RANDOM_NUMBER)

    /** 13 建立加密链路 —— 真机实测为明文固定帧（响应才是密文）。 */
    fun frameEncryptedLinkEstablish(): String = FIXED_FRAMES.getValue(CMD_ENCRYPTED_LINK_ESTABLISH)

    /** 0B 开锁 —— 密文帧，content 为空。 */
    fun frameUnlock(randomNumberProtocol: String, activationKey: String): String? =
        build(CMD_UNLOCK, "", randomNumberProtocol, activationKey)

    /** 01 电量 —— 真机实测为密文帧（常量表里的明文帧未被使用）。 */
    fun frameBattery(randomNumberProtocol: String, activationKey: String): String? =
        build(CMD_BATTERY_LEVEL, "", randomNumberProtocol, activationKey)

    /** 激活阶段：用 DEFAULT_AES_KEY 生成密钥。 */
    fun frameGenerateKey(randomNumberProtocol: String): String? =
        build(CMD_GENERATE_KEY, "", randomNumberProtocol, DEFAULT_AES_KEY)

    /** 激活阶段：提交激活。 */
    fun frameActivate(randomNumberProtocol: String, activationKey: String): String? =
        build(CMD_ACTIVATE, "", randomNumberProtocol, activationKey)

    // ==================================================================
    // 切包
    // ==================================================================

    // ==================================================================
    // 帧长度精确判定（P4 提速核心）
    // ==================================================================

    /**
     * 从帧头推算整帧字节数 —— 用于**精确判定消息边界**，替代 App 那 200ms 空闲等待。
     *
     * 帧型靠 `55AA` 后的首字节区分（真机逐字节验证）：
     *   明文帧 55AA | FF | cmd(1B) | len(2B,大端) | payload(len) | cksum → 总长 = 7 + len
     *   密文帧 55AA | len(2B,大端) | cipher(len)   | cksum               → 总长 = 5 + len
     *
     * 实测：0D 响应 55AAFF0D0011… → 7+0x11 = 24 字节
     *      13/0B ack 55AA0010…   → 5+0x10 = 21 字节
     *
     * 数据不足 / 帧头非法 → null（调用方回退到空闲超时兜底）。
     */
    fun expectedFrameLength(buf: ByteArray): Int? {
        if (buf.size < 4) return null
        if (buf[0] != 0x55.toByte() || buf[1] != 0xAA.toByte()) return null
        if (buf[2] == 0xFF.toByte()) {                       // 明文帧
            if (buf.size < 6) return null
            val payloadLen = ((buf[4].toInt() and 0xFF) shl 8) or (buf[5].toInt() and 0xFF)
            return 7 + payloadLen
        }
        // 密文帧
        val cipherLen = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
        if (cipherLen == 0 || cipherLen > 512) return null
        return 5 + cipherLen
    }

    /** 缓冲里是否已够一整帧（长度判定）。 */
    fun frameIsComplete(buf: ByteArray): Boolean {
        val want = expectedFrameLength(buf) ?: return false
        return buf.size >= want
    }

    /** 把帧 hex 按 20 字节切包，逐包 writeCharacteristic。 */
    fun splitPackets(frameHex: String, chunk: Int = WRITE_CHUNK): List<ByteArray> {
        val data = hexToBytes(frameHex)
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i < data.size) {
            val end = minOf(i + chunk, data.size)
            out.add(data.copyOfRange(i, end))
            i = end
        }
        return out
    }
}
