package com.fktm.app.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VocProtocolTest —— Kotlin 协议层回归测试
 *
 * 黄金向量是**合成**的：用一把假钥匙（DEADBEEF…）配上三组随机数，现场算出来的。
 * 为什么不用真机报文：activationKey 就是这把门的钥匙，而
 *   actualAesKey = randomNumber XOR activationKey，
 * 两者同时出现在仓库里 = 钥匙明文公开，拿到就能离线开门。
 *
 * 断言同样是逐字节锁定，任何改动只要破坏兼容性，这里必须当场 FAIL。
 * 与 Python 侧 `fktm/cli.py selftest` 第 9 节等价。
 */
class VocProtocolTest {

    private val activationKey = "DEADBEEFCAFEBABE0123456789ABCDEF"

    // ---- 会话1（UNLOCK）----
    private val s1Rnd = "55AAFF0D0011003F9A21C7D40B58E6A1C93D7B25F80E64AB"
    private val s1Aes = "E1379F281EF5E258A0EA781CAC53C38B"
    private val s1_13 = "55AAFF13000011"
    private val s1_0B = "55AA0010C2A92FBC07CDE8CFF43D02BDCC4FC46C2B"
    private val s1_13Ack = "55AA0010D4E2E98F44E2F214EB60313F1DDAA703C5"
    private val s1_0BAck = "55AA00102B6E4292BA050E28CF910BEA99867599F3"

    // ---- 会话2（BATTERY）----
    private val s2Rnd = "55AAFF0D0011007C4E0B93A6F1D85230E7B4C91F6A2D58E7"
    private val s2Aes = "A2E3B57C6C0F62EC31C4F1AE96C1E0B7"
    private val s2_01 = "55AA00109565EF5BE95123343CC3CDA8DD15F7DE1F"

    // ---- 会话3（独立随机数，同一把钥匙跨会话复用）----
    private val s3Rnd = "55AAFF0D001100B5D1F0A824E67C3918BA4F2D60C9E7358C"
    private val s3Aes = "6B7C4E47EE18C68719990A4AE9622ADA"
    private val s3_0B = "55AA001000145A06C309B2ECA8D3EB3C6469487216"
    private val s3_13Ack = "55AA00108E2ED64A348AB2A4A23217410830656C34"
    private val s3_0BAck = "55AA001056AC5D5EDB353C73F1816FE353A85741E2"

    // ==================================================================
    // 1. 基础算法
    // ==================================================================

    @Test
    fun aesMatchesNistFips197Vector() {
        // FIPS-197 / SP 800-38A AES-128 ECB 标准向量
        val ct = VocProtocol.aesEncryptHex(
            "00112233445566778899aabbccddeeff",
            "000102030405060708090a0b0c0d0e0f"
        )
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", ct)
    }

    @Test
    fun aesRoundTrip() {
        val key = activationKey
        val pt = "ff0b0000090000000000000000000000"
        val ct = VocProtocol.aesEncryptHex(pt, key)
        assertEquals(pt, VocProtocol.aesDecryptHex(ct, key))
    }

    @Test
    fun computeValidateCodeMatchesFixedFrames() {
        assertEquals("0b", VocProtocol.computeValidateCode("55AAFF0D0000"))
        assertEquals("11", VocProtocol.computeValidateCode("55AAFF130000"))
        assertEquals("0a", VocProtocol.computeValidateCode("55AAFF0C0000"))
    }

    @Test
    fun xorZeroWithAllF() {
        // 与 Python 侧 selftest 第 4 节一致：content=16×0x00, key="0F"×16
        assertEquals("0f".repeat(16), VocProtocol.xor("00".repeat(16), "0F".repeat(16)))
    }

    @Test
    fun assembleDistributeContentUnlockIsEmptyBody() {
        // FF | 0B | 0000 | cksum(09) | 补零到 32 hex
        // 注意：前导 "FF" 是字面量（大写），与 Python 版一致
        assertEquals("FF0B0000090000000000000000000000",
            VocProtocol.assembleDistributeContent(VocProtocol.CMD_UNLOCK, ""))
    }

    @Test
    fun macFormatReversesBytes() {
        assertEquals("06:05:04:03:02:01", VocProtocol.formatMacAddress("010203040506"))
        assertNull(VocProtocol.formatMacAddress("0102"))
    }

    @Test
    fun macFromBroadcastSkipsFfPlusFour() {
        // 布局：广播头(4 hex) + "FF" + 1 字节 + 6 字节 MAC(待逆序)
        // 起点 = 首个 "FF" 的下标 + 4 个 hex 字符（即 2 字节）
        assertEquals("FF:EE:DD:CC:BB:AA",
            VocProtocol.extractMacFromBroadcast("0102FF03AABBCCDDEEFF"))
    }

    @Test
    fun splitPacketsChunksAt20Bytes() {
        // 21 字节帧 → [20B, 1B]（真机 P2 现场就是 20+1 两包）
        val pkts = VocProtocol.splitPackets(s3_0B)
        assertEquals(2, pkts.size)
        assertEquals(20, pkts[0].size)
        assertEquals(1, pkts[1].size)
    }

    // ==================================================================
    // 2. 反解 activationKey（日志没打印也能拿到）
    // ==================================================================

    @Test
    fun recoverActivationKeyFromAllThreeSessions() {
        // activationKey = randomNumber XOR actualAesKey
        val k1 = VocProtocol.xor(VocProtocol.fetchRandomNumber(s1Rnd), s1Aes).uppercase()
        val k2 = VocProtocol.xor(VocProtocol.fetchRandomNumber(s2Rnd), s2Aes).uppercase()
        val k3 = VocProtocol.xor(VocProtocol.fetchRandomNumber(s3Rnd), s3Aes).uppercase()
        assertEquals(activationKey, k1)
        assertEquals(activationKey, k2)
        assertEquals(activationKey, k3)
    }

    @Test
    fun derivedAesKeyMatchesAppPrint() {
        assertEquals(s1Aes, VocProtocol.xor(VocProtocol.fetchRandomNumber(s1Rnd), activationKey).uppercase())
        assertEquals(s2Aes, VocProtocol.xor(VocProtocol.fetchRandomNumber(s2Rnd), activationKey).uppercase())
        assertEquals(s3Aes, VocProtocol.xor(VocProtocol.fetchRandomNumber(s3Rnd), activationKey).uppercase())
    }

    // ==================================================================
    // 3. 上行帧逐字节一致
    // ==================================================================

    @Test
    fun linkEstablishIsClearFixedFrame() {
        assertEquals(s1_13, VocProtocol.frameEncryptedLinkEstablish())
        assertEquals("55AAFF13000011", VocProtocol.frameEncryptedLinkEstablish())
    }

    @Test
    fun unlockFramesMatchAllSessions() {
        assertEquals(s1_0B, VocProtocol.frameUnlock(s1Rnd, activationKey))
        assertEquals(s3_0B, VocProtocol.frameUnlock(s3Rnd, activationKey))
    }

    @Test
    fun batteryFrameMatchesVector() {
        assertEquals(s2_01, VocProtocol.frameBattery(s2Rnd, activationKey))
    }

    @Test
    fun requestRandomNumberFixedFrame() {
        assertEquals("55AAFF0D00000B", VocProtocol.frameRequestRandomNumber())
    }

    // ==================================================================
    // 4. 上行清零验证 + 下行解密
    // ==================================================================

    @Test
    fun unlockPlaintextHasEmptyContent() {
        val plain = VocProtocol.aesDecryptHex(VocProtocol.extractMessage(s1_0B), s1Aes)
        assertEquals("ff0b0000090000000000000000000000", plain)
        // 业务内容为空 → 门锁只认 activationKey
        val p = plain!!
        assertEquals("09", p.substring(8, 10))
        assertTrue(p.substring(10).all { it == '0' })
    }

    @Test
    fun downlinkAcksDecryptToStatus00() {
        val r13 = VocProtocol.parse(s1_13Ack, s1Rnd, activationKey)
        assertTrue(r13.ok)
        assertEquals("00", r13.detail!!.substring(8, 10))

        val r0b = VocProtocol.parse(s1_0BAck, s1Rnd, activationKey)
        assertTrue(r0b.ok)
        assertEquals("00", r0b.detail!!.substring(8, 10))

        val p13 = VocProtocol.parse(s3_13Ack, s3Rnd, activationKey)
        assertTrue(p13.ok)
        assertEquals("00", p13.detail!!.substring(8, 10))

        val p0b = VocProtocol.parse(s3_0BAck, s3Rnd, activationKey)
        assertTrue(p0b.ok)
        assertEquals("00", p0b.detail!!.substring(8, 10))
    }

    @Test
    fun linkEstablishedHelperMatchesVectorAck() {
        assertTrue(VocProtocol.isLinkEstablished(s1_13Ack, s1Rnd, activationKey))
        assertTrue(VocProtocol.isLinkEstablished(s3_13Ack, s3Rnd, activationKey))
    }

    @Test
    fun wrongKeyMustFailLinkEstablish() {
        assertFalse(VocProtocol.isLinkEstablished(s1_13Ack, s1Rnd, "FF".repeat(16)))
    }

    @Test
    fun unlockBareAckRecognised() {
        assertTrue(VocProtocol.UNLOCK_SUCCESS_ACK == "55AAFF0B0001000A")
    }

    @Test
    fun errorCodeMapIsComplete() {
        assertEquals("锁体不适配", VocProtocol.CODE_MAP["14"])
        assertEquals("超时", VocProtocol.CODE_MAP["02"])
    }

    @Test
    fun buildReturnsNullWithoutRandom() {
        assertNull(VocProtocol.build(VocProtocol.CMD_UNLOCK, "", null, activationKey))
    }

    // ==================================================================
    // 5. 跨语言等价性（Python 侧随机生成 → Kotlin 侧复算）
    // ==================================================================

    private data class Case(
        val key: String,
        val rnd: String,
        val unlock: String,
        val battery: String,
    )

    @Test
    fun crossLanguageParityWithPython() {
        // 数据由 fktm/voc_protocol.py 现场随机生成（随机数 + 随机 activationKey），
        // 覆盖黄金向量之外的随机路径，防止"刚好对上"的巧合。
        val cases = listOf(
            Case(
                "6A60AF1E718FDB01C83575EB3DC9B954",
                "55AAFF0D0011002B0FA6D11936A9843C76538C197336A844",
                "55AA001024034999876396830454085BFA06E3D58E",
                "55AA00100F6E2CB29348B90AF43FD6AFF9D48DB0CA",
            ),
            Case(
                "B2013A24E8AE0C18BC5A742ADD417B71",
                "55AAFF0D0011002CE5C01B7EB09861739919880EA5957CA0",
                "55AA0010388AE2C273AAF935AD852184C1B92B619D",
                "55AA00101310820159389E23293C233C2100AA7F15",
            ),
            Case(
                "44449EACD82541E3C81C699689B9D7C6",
                "55AAFF0D001100486851660AFA71B7E87EC24E9048D8FBD0",
                "55AA0010A1C711436B059CCBB569D2517C228D2937",
                "55AA0010A13766BD08E9195C08CB0E23D113EC397D",
            ),
            Case(
                "6677C2D804EE1613B9A718A9E71AC7E7",
                "55AAFF0D0011004A0E929FFB4F246438CB572B2952689170",
                "55AA00102555B111C96416BA0D60C32537A4219932",
                "55AA00104C1E1792E7604457175D372A514C31D279",
            ),
            Case(
                "2A8E4FD54A8BDC72E4009621E834F887",
                "55AAFF0D00110099DDFCBCF8B02233499C1F58E7BFD1A4BE",
                "55AA0010782A01471935A543FCBCC7D68949604D03",
                "55AA001040B69BD3EC6DF1BB0CAE6DA0A926E105F4",
            ),
        )
        for ((i, c) in cases.withIndex()) {
            assertEquals("case$i 0B 帧与 Python 不一致", c.unlock, VocProtocol.frameUnlock(c.rnd, c.key))
            assertEquals("case$i 01 帧与 Python 不一致", c.battery, VocProtocol.frameBattery(c.rnd, c.key))
            // 派生 key 再反解回 activationKey（往返一致）
            val random = VocProtocol.fetchRandomNumber(c.rnd)
            val derived = VocProtocol.xor(random, c.key)
            assertEquals("case$i 反解 key 失败", c.key, VocProtocol.xor(random, derived).uppercase())
        }
    }

    // ==================================================================
    // 帧长度精确判定（P4 提速核心：替代 App 的 200ms 空闲等待）
    // 明文帧 7+len（0D 响应 24B） / 密文帧 5+len（13、0B ack 21B）
    // ==================================================================

    @Test
    fun expectedFrameLengthMatchesVectorFrames() {
        assertEquals(24L, (VocProtocol.expectedFrameLength(VocProtocol.hexToBytes(s1Rnd)) ?: -1).toLong())
        assertEquals(21L, (VocProtocol.expectedFrameLength(VocProtocol.hexToBytes(s1_13Ack)) ?: -1).toLong())
        assertEquals(21L, (VocProtocol.expectedFrameLength(VocProtocol.hexToBytes(s1_0BAck)) ?: -1).toLong())
        assertEquals(21L, (VocProtocol.expectedFrameLength(VocProtocol.hexToBytes(s3_0BAck)) ?: -1).toLong())
        assertEquals(7L, (VocProtocol.expectedFrameLength(VocProtocol.hexToBytes(s1_13)) ?: -1).toLong())
    }

    @Test
    fun expectedFrameLengthCoversAllVectorFramesExactly() {
        val frames = listOf(
            s1Rnd, s2Rnd, s3Rnd, s1_13, s1_13Ack, s1_0BAck, s2_01, s3_13Ack, s3_0BAck, s3_0B,
        )
        for (f in frames) {
            val bytes = VocProtocol.hexToBytes(f)
            val want = VocProtocol.expectedFrameLength(bytes)
            assertEquals("帧长判定与实际字节数不一致: $f", bytes.size, want)
        }
    }

    @Test
    fun frameIsCompleteOnlyWhenFullFrameBuffered() {
        val full = VocProtocol.hexToBytes(s1Rnd)
        assertFalse("只收 19 字节不应判为完整帧", VocProtocol.frameIsComplete(full.copyOfRange(0, 19)))
        assertTrue("收满 24 字节应判为完整帧", VocProtocol.frameIsComplete(full))
    }

    @Test
    fun expectedFrameLengthRejectsInvalidInput() {
        assertNull(VocProtocol.expectedFrameLength(byteArrayOf()))
        assertNull(VocProtocol.expectedFrameLength(byteArrayOf(0x55.toByte(), 0xAA.toByte())))
        assertNull(
            VocProtocol.expectedFrameLength(
                byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05).map { it.toByte() }.toByteArray()
            )
        )
    }

    @Test
    fun speedupConstantsRegression() {
        // 提速参数：包间 20ms、兜底 60ms（原 200ms）
        assertEquals(20L, VocProtocol.WRITE_DELAY_MS)
        assertEquals(60L, VocProtocol.MESSAGE_FALLBACK_IDLE_MS)
        assertTrue(VocProtocol.MESSAGE_FALLBACK_IDLE_MS < VocProtocol.MESSAGE_READ_INTERVAL_MS)
    }
}
