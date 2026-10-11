# -*- coding: utf-8 -*-
"""
voc_protocol.py — VOC 门锁 BLE 协议复刻层 (fkTM P1)

严格逐字节对齐天猫校园 App 的以下反编译产物：
    com.tmall.campus.doorlock.bluetooth.BluetoothHelper      (协议编解码核心)
    com.tmall.campus.doorlock.bluetooth.VocCommandType       (命令码表)
    com.tmall.campus.doorlock.VocStatistics                  (常量/工作流)
    com.tmall.campus.doorlock.VocLockHelper                  (切包/MAC/消息重组)
    com.tmall.campus.utils.AESUtils                          (AES/ECB/NoPadding)

所有函数名尽量与 Java 原方法一一对应，便于回归比对。

--------------------------------------------------------------------------
帧格式（两种，别混）
--------------------------------------------------------------------------
[明文帧 / clear frame]   用于 0D 命令与固定指令，也被用作开锁成功裸 ack
    55AA | FF | cmd(1B) | len(2B,大端) | payload(len B) | cksum(1B)
    cksum = (本帧 cksum 之前所有字节之和) % 256
    例: 55AAFF0D00000B   (请求随机数, len=0)
        55AAFF0B0001000A (开锁成功 ack, len=1, payload=00)

[密文帧 / cipher frame]  由 build() 产出，用于 0B 开锁 / 01 电量 / 06 激活 / 0E 生成密钥
    55AA | len(2B,大端) | cipher(len B) | cksum(1B)
    其中 cipher = AES-128/ECB/NoPadding( 明文明文blob, actualAesKey )
    明文blob = assembleDistributeContent(sign, content) = FF|sign|len(2B)|content|cksum, 补 '0' 对齐 32 hex

[P0 真机校正 2026-10-11] 「13 建立加密链路」**不是密文帧**，而是明文固定帧
    55AAFF13000011（见 frame_encrypted_link_establish）。只有它的**响应**才是密文帧。
    真机各命令的帧型（已逐字节验证）：
        0D 请求随机数     → 明文固定帧  55AAFF0D00000B      响应=明文(含16B随机数)
        13 建立加密链路   → 明文固定帧  55AAFF13000011      响应=密文(需解密)
        0B 开锁           → 密文帧 (content 空)             响应=密文
        01 电量           → 密文帧 (content 空)             响应=密文

> 两种帧靠「55AA 之后第一个字节」区分：0xFF = 明文帧，否则视为密文帧长度高位。
> 该判定属 P0 现场复核项（见 fkTM-PLAN.md §5）。

--------------------------------------------------------------------------
密钥派生（challenge-response）
--------------------------------------------------------------------------
  randomNumber = 0D 响应的 [14:46]（32 hex = 16 字节）
  actualAesKey = randomNumber XOR activationKey 的最后 16 字节
  开锁明文 content 为空 → 每把锁只要拿到 activationKey 即可永久离线开锁
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional, Tuple

from Crypto.Cipher import AES

# ==========================================================================
# 常量 (VocStatistics / BluetoothHelper / VocCommandType)
# ==========================================================================

PROTOCOL_HEADER = "55AA"
DEFAULT_AES_KEY = "564F435F426C65536D6172744C6F636B"   # ASCII "VOC_BleSmartLock"
UNLOCK_SUCCESS_ACK = "55AAFF0B0001000A"
VOC_LOCK_TAG = "~vTmallLock"
CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
DEFAULT_SERVICE_UUID = "00002760-08c2-11e1-9073-0e8ac72e1001"

DEFAULT_MTU = 23
WRITE_CHUNK = 20            # 每次 writeCharacteristic 最大字节数 (VocLockHelper.writeValue)
WRITE_DELAY_MS = 20         # 包间隔（原 50ms；帧长已知 + 写回调确认后无需长等）
MESSAGE_READ_INTERVAL_MS = 200   # App 原始做法：消息边界空闲阈值（保留作兼容/兜底参考）
MESSAGE_FALLBACK_IDLE_MS = 60    # 提速：长度判定失败时的兜底空闲阈值（远小于 200ms）
DEFAULT_SESSION_TIMEOUT_MS = 50000

# 命令码 (sign) — VocCommandType
CMD_REQUEST_RANDOM_NUMBER = "0D"
CMD_ENCRYPTED_LINK_ESTABLISH = "13"
CMD_BATTERY_LEVEL = "01"
CMD_UNLOCK = "0B"
CMD_GENERATE_KEY = "0E"
CMD_ACTIVATE = "06"
CMD_RESET = "0C"

# 固定明文帧（直接按字节下发，不经 AES）— VocCommandType.commandHex
FIXED_FRAMES = {
    CMD_REQUEST_RANDOM_NUMBER: "55AAFF0D00000B",
    CMD_ENCRYPTED_LINK_ESTABLISH: "55AAFF13000011",
    CMD_BATTERY_LEVEL: "55AAFF010000FF",
    CMD_RESET: "55AAFF0C00000A",
}

# 命令码 → 上行帧型（P0 真机校正）
#   True  = 明文固定帧（直接下发 FIXED_FRAMES[cmd]）
#   False = 密文帧（用 build(cmd, "", random, key) 现场生成）
CMD_IS_CLEAR_FRAME = {
    CMD_REQUEST_RANDOM_NUMBER: True,     # 55AAFF0D00000B
    CMD_ENCRYPTED_LINK_ESTABLISH: True,  # 55AAFF13000011
    CMD_BATTERY_LEVEL: False,            # 真机走密文（常量表里的明文帧未被使用）
    CMD_UNLOCK: False,
    CMD_GENERATE_KEY: False,
    CMD_ACTIVATE: False,
    CMD_RESET: True,                     # 未在真机验证；按常量表暂列明文，调试严禁下发
}

# 错误码 — BluetoothHelper.CODE_MAP
CODE_OK = "00"
CODE_MAP = {
    "01": "格式错误",
    "02": "超时",
    "03": "ID已存在",
    "04": "添加失败",
    "05": "删除失败",
    "06": "修改失败",
    "07": "ID已满",
    "08": "手动取消",
    "09": "等待",
    "0a": "ID不存在",
    "0b": "门锁未进入待激活状态",
    "0c": "系统锁定中",
    "0d": "请先关闭安全模式",
    "0e": "请先开启所有开门方式",
    "0f": "超出规定绑定时间",
    "10": "超出规定时间内的绑定次数",
    "11": "密码过于简单",
    "12": "锁体信息查询失败",
    "13": "锁体配置失败",
    "14": "锁体不适配",
}

_RANDOM_FRAME_RE = r"^55AA[0-9A-Fa-f]{2}0D0011[0-9A-Fa-f]{2}[0-9A-Fa-f]{32}[0-9A-Fa-f]{2}$"


# ==========================================================================
# 基础工具
# ==========================================================================

def _require_even_hex(s: str, what: str = "hex") -> str:
    if s is None:
        raise ValueError(f"{what} 为 None")
    if len(s) % 2 != 0:
        raise ValueError(f"{what} 长度必须为偶数: {s!r}")
    return s


def compute_validate_code(content: str) -> str:
    """BluetoothHelper.computeValidateCode — 字节和 % 256，输出 2 位小写 hex。"""
    total = 0
    for i in range(0, len(content), 2):
        total += int(content[i:i + 2], 16)
    return "%02x" % (total % 256)


def xor(content: str, hex_string: str) -> str:
    """BluetoothHelper.xor — content 逐字节 XOR key 的末 16 字节(循环)。"""
    key = hex_string[len(hex_string) - 32:]
    out = []
    n = len(content) // 2
    for i in range(n):
        b = int(content[i * 2:i * 2 + 2], 16)
        k = int(key[(i % 16) * 2:(i % 16) * 2 + 2], 16)
        out.append("%02x" % (b ^ k))
    return "".join(out)


def fetch_random_number(content: str) -> str:
    """BluetoothHelper.fetchRandomNumber — 从 0D 响应帧里取出 16 字节随机数(32 hex)。"""
    upper = content.upper()
    if not __import__("re").match(_RANDOM_FRAME_RE, upper):
        raise ValueError("随机数不匹配")
    return upper[14:46]


def format_mac_address(mac12: str) -> Optional[str]:
    """BluetoothHelper.formatMacAddress — 12 hex 字节逆序后按 ':' 拼接。"""
    if mac12 is None or len(mac12) != 12:
        return None
    parts = [mac12[i:i + 2] for i in range(len(mac12) - 2, -1, -2)]
    return ":".join(parts)


def extract_mac_from_broadcast(broadcast_hex: str) -> Optional[str]:
    """VocLockHelper.findDevices 中的广播解析：找首个 'FF'，其后偏移 4 起取 12 hex，再逆序。"""
    upper = broadcast_hex.upper()
    idx = upper.find("FF")
    if idx < 0:
        return None
    start = idx + 4
    seg = upper[start:start + 12]
    if len(seg) != 12:
        return None
    return format_mac_address(seg)


# ==========================================================================
# AES-128/ECB/NoPadding  (AESUtils.encryptHexString / decryptHexString)
# ==========================================================================

def _aes_key_bytes(key_hex: str) -> bytes:
    kb = bytes.fromhex(_require_even_hex(key_hex, "aesKey"))
    if len(kb) < 16:
        raise ValueError("AES key 至少需要 16 字节")
    return kb[:16]   # AESUtils 用前 16 字节 (Math.min(len,16))


def aes_encrypt_hex(plaintext_hex: str, key_hex: str) -> str:
    pt = bytes.fromhex(_require_even_hex(plaintext_hex, "plaintext"))
    if len(pt) % 16 != 0:
        raise ValueError(f"NoPadding 要求明文长度为 16 的整数倍，当前 {len(pt)}")
    return AES.new(_aes_key_bytes(key_hex), AES.MODE_ECB).encrypt(pt).hex()


def aes_decrypt_hex(cipher_hex: str, key_hex: str) -> Optional[str]:
    ct = bytes.fromhex(_require_even_hex(cipher_hex, "cipher"))
    if len(ct) == 0 or len(ct) % 16 != 0:
        return None
    try:
        return AES.new(_aes_key_bytes(key_hex), AES.MODE_ECB).decrypt(ct).hex()
    except Exception:
        return None


# ==========================================================================
# 明文 blob 组装  (BluetoothHelper.assembleDistributeContent)
# ==========================================================================

def assemble_distribute_content(sign: str, content: str) -> str:
    """FF | sign | contentLen(2B) | content | cksum  → 补 '0' 对齐 32 hex。"""
    core = "FF" + sign + "%04x" % (len(content) // 2) + content
    core += compute_validate_code(PROTOCOL_HEADER + core)
    pad = (-len(core)) % 32
    return core + ("0" * pad)


# ==========================================================================
# 组帧 / 解帧  (BluetoothHelper.build / parse / extractMessage)
# ==========================================================================

def build(sign: str, content: str, random_number_protocol: Optional[str],
          aes_key: str) -> Optional[str]:
    """
    密文帧组装。对应 BluetoothHelper.build(sign, content, randomNumberProtocol, aesKey)。
    random_number_protocol: 完整的 0D 响应帧 hex（不是裸随机数）
    aes_key:                activationKey（开锁/重置/电量）或 DEFAULT_AES_KEY（激活阶段）
    """
    if not random_number_protocol:
        return None
    try:
        actual_key = xor(fetch_random_number(random_number_protocol), aes_key)
        cipher = aes_encrypt_hex(assemble_distribute_content(sign, content), actual_key)
        head = PROTOCOL_HEADER + "%04x" % (len(cipher) // 2) + cipher
        return (head + compute_validate_code(head)).upper()
    except Exception:
        return None


def extract_message(content: str) -> str:
    """BluetoothHelper.extractMessage — 从密文帧 55AA|len|cipher|cksum 中取 cipher。"""
    try:
        upper = content.upper()
        n = int(upper[4:8], 16)
        return upper[8:8 + n * 2]
    except Exception:
        return ""


def extract_data(original_message: str) -> str:
    """BluetoothHelper.extractData — 从明文帧 55AA|FF|cmd|len|payload|cksum 中取 payload。"""
    try:
        n = int(original_message[4:8], 16)
        return original_message[10:8 + n * 2]
    except Exception:
        return ""


def parse(content_protocol: str, random_number_protocol: Optional[str],
          aes_key: Optional[str]) -> Tuple[bool, Optional[str]]:
    """
    解帧并校验。对应 BluetoothHelper.parse(...)。
    返回 (ok, detail)：
        成功 → (True, 解密后全文 hex)
        失败 → (False, 错误描述 / 错误码文案)
    """
    if not random_number_protocol or not aes_key:
        return (False, None)
    try:
        actual_key = xor(fetch_random_number(random_number_protocol), aes_key)
        plain = aes_decrypt_hex(extract_message(content_protocol), actual_key)
        if plain is None:
            return (False, None)
        if not plain.startswith("ff"):
            return (False, "密钥丢失,请联系厂家或经销商")
        status = plain[8:10]
        if status != CODE_OK:
            return (False, CODE_MAP.get(status, f"未知状态码 {status}"))
        return (True, plain)
    except Exception as e:
        return (False, str(e))


def get_error_code(ack: str) -> str:
    return ack[8:10]


def is_link_established(establish_ack_hex: str, random_number_protocol: str,
                        activation_key: str) -> bool:
    """VocLockHelper.isLinkEstablished（开锁流程里 key 就是 activationKey）。"""
    ok, _ = parse(establish_ack_hex, random_number_protocol, activation_key)
    return ok


# ==========================================================================
# 工作流帧构造器（给 P2/P3 直接用）
# ==========================================================================

def frame_request_random_number() -> str:
    return FIXED_FRAMES[CMD_REQUEST_RANDOM_NUMBER]


def frame_encrypted_link_establish(random_number_protocol: Optional[str] = None,
                                   activation_key: Optional[str] = None) -> str:
    """
    13 建立加密链路。

    【P0 真机校正 2026-10-11】真机抓包证明 13 是**明文固定帧** `55AAFF13000011`，
    不走 AES —— 只有**它的响应**才是密文帧（用 activationKey 派生 key 解密）。
    前一版按 build() 生成密文帧属复刻偏差，已修正。
    参数保留仅为兼容旧调用，实际被忽略。
    """
    return FIXED_FRAMES[CMD_ENCRYPTED_LINK_ESTABLISH]


def frame_unlock(random_number_protocol: str, activation_key: str) -> Optional[str]:
    """开锁帧：content 为空。对应 VocLockHelper$unlock$1 → build("0B", "", random, activationKey)。"""
    return build(CMD_UNLOCK, "", random_number_protocol, activation_key)


def frame_battery(random_number_protocol: str, activation_key: str) -> Optional[str]:
    """
    电量查询帧。

    【P0 真机校正 2026-10-11】真机日志里 BATTERY_LEVEL 走的是 `VocUnlockBridge.build:`
    → 是**密文帧** `55AA|len|cipher|cksum`，不是 VocCommandType 常量表里的明文
    `55AAFF010000FF`。以真机行为为准。
    """
    return build(CMD_BATTERY_LEVEL, "", random_number_protocol, activation_key)


def frame_battery_fixed() -> str:
    """VocCommandType 常量表里的电量明文帧（真机未走这条，仅留作对照）。"""
    return FIXED_FRAMES[CMD_BATTERY_LEVEL]


def frame_reset() -> str:
    return FIXED_FRAMES[CMD_RESET]


def frame_generate_key(random_number_protocol: str) -> Optional[str]:
    """激活阶段用 DEFAULT_AES_KEY 生成密钥。"""
    return build(CMD_GENERATE_KEY, "", random_number_protocol, DEFAULT_AES_KEY)


def frame_activate(random_number_protocol: str, activation_key: str) -> Optional[str]:
    return build(CMD_ACTIVATE, "", random_number_protocol, activation_key)


# ==========================================================================
# 明文帧构造（模拟门锁侧 / 校验用）
# ==========================================================================

def build_clear_frame(cmd: str, payload_hex: str = "") -> str:
    """明文帧：55AA|FF|cmd|len|payload|cksum（统一大写，与常量表一致）。"""
    so_far = PROTOCOL_HEADER + "FF" + cmd + "%04x" % (len(payload_hex) // 2) + payload_hex
    return (so_far + compute_validate_code(so_far)).upper()


def build_random_response(random_hex: str, prefix_byte: str = "00") -> str:
    """
    构造 0D 响应明文帧：55AA|FF|0D|0011|XX|<16B random>|cksum
    XX(prefix_byte) 语义未知（P0 复核项），且不含在 [14:46] 内，不影响密钥派生。
    """
    if len(random_hex) != 32:
        raise ValueError("random 必须是 32 hex")
    payload = prefix_byte + random_hex
    return build_clear_frame(CMD_REQUEST_RANDOM_NUMBER, payload)


# ==========================================================================
# 切包 / 消息重组（VocLockHelper.writeValue + onCharacteristicChanged）
# ==========================================================================

def expected_frame_length(buf: bytes) -> Optional[int]:
    """
    从帧头推算整帧字节数 —— 用于**精确判定消息边界**，替代 200ms 空闲等待。

    帧型靠 `55AA` 后的首字节区分（真机逐字节验证）：
      明文帧  55AA | FF | cmd(1B) | len(2B,大端) | payload(len) | cksum(1B) → 总长 = 7 + len
      密文帧  55AA | len(2B,大端) | cipher(len)   | cksum(1B)               → 总长 = 5 + len

    实测: 0D 响应 55AAFF0D0011… → 7+0x11 = 24 字节
          13/0B ack 55AA0010…   → 5+0x10 = 21 字节

    数据不足 / 帧头非法 → None（调用方回退到空闲超时兜底）。
    """
    if len(buf) < 4:
        return None
    if buf[0] != 0x55 or buf[1] != 0xAA:
        return None
    if buf[2] == 0xFF:                      # 明文帧
        if len(buf) < 6:
            return None
        payload_len = (buf[4] << 8) | buf[5]
        return 7 + payload_len
    cipher_len = (buf[2] << 8) | buf[3]     # 密文帧
    if cipher_len == 0 or cipher_len > 512:
        return None
    return 5 + cipher_len


def frame_is_complete(buf: bytes) -> bool:
    """缓冲里是否已够一整帧（长度判定）。"""
    want = expected_frame_length(buf)
    return want is not None and len(buf) >= want


def split_packets(frame_hex: str, chunk: int = WRITE_CHUNK) -> List[bytes]:
    """把帧 hex 按 20 字节切包，逐包 writeCharacteristic。"""
    data = bytes.fromhex(_require_even_hex(frame_hex, "frame"))
    return [data[i:i + chunk] for i in range(0, len(data), chunk)]


@dataclass
class MessageAssembler:
    """
    复刻 VocLockHelper 的接收缓冲：BLE 分片 notify 累积，
    空闲 MESSAGE_READ_INTERVAL_MS 后视为一条完整消息 (onMessageReceiveEnd)。
    """
    idle_ms: int = MESSAGE_READ_INTERVAL_MS
    _buf: bytearray = None  # type: ignore

    def __post_init__(self):
        self._buf = bytearray()
        self._last_ts = 0.0

    def feed(self, chunk: bytes, now: float) -> None:
        self._buf.extend(chunk)
        self._last_ts = now

    def is_message_complete(self, now: float) -> bool:
        return len(self._buf) > 0 and (now - self._last_ts) * 1000.0 >= self.idle_ms

    def take(self) -> str:
        s = bytes(self._buf).hex().upper()
        self._buf.clear()
        return s


# ==========================================================================
# 凭证
# ==========================================================================

@dataclass
class LockProfile:
    mac: str                                  # 门锁 MAC（正常大小写，冒号分隔）
    activation_key: str                        # 开锁密钥（=钥匙，务必加密存储）
    advertis_string: Optional[str] = None      # 广播原文（可选，用于序列号）
    name: str = "宿舍门"

    def serial_number(self) -> str:
        try:
            return (self.advertis_string or "")[10:28]
        except Exception:
            return ""
