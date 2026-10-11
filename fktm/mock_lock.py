# -*- coding: utf-8 -*-
"""
mock_lock.py — VOC 门锁离线模拟器 + 客户端链路模拟 (fkTM P1/P2)

目的：
  1) P1 回归对照：在无设备、无天猫校园的前提下，验证 voc_protocol.py 的
     「请求随机数 → 建立加密链路 → 开锁」全流程自洽。
  2) P2 蓝图：SimulatedLink 的实现结构与 P2 真实 BLE 客户端一一对应，
     真实实现只要把 _transport 换成 bleak / BluetoothGatt 即可。

模拟器严格遵循 Chat 中还原出的协议；任何一步不一致都会导致解密失败，
因此它同时充当协议复刻的「语法校验器」。
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Callable, List, Optional, Tuple

import voc_protocol as P


# ==========================================================================
# 门锁侧
# ==========================================================================

class MockLockError(Exception):
    pass


@dataclass
class MockLock:
    """
    一把模拟 VOC 门锁。

    activation_key : 真实门锁上烧录的密钥（= 钥匙）。客户端拿错就打不开。
    ack_mode       : 'encrypted' 返回密文 ack（走完整 AES 校验路径）
                     'clear'     返回裸 ack 55AAFF0B0001000A（走 startsWith 捷径）
    force_status   : 非 None 时，强制返回某个错误码（用于验证错误分支）
    """
    activation_key: str = "00112233445566778899AABBCCDDEEFF"
    ack_mode: str = "encrypted"
    force_status: Optional[str] = None

    current_random_hex: Optional[str] = None   # 本次会话下发的 16B 随机数
    current_random_frame: Optional[str] = None  # 完整的 0D 响应帧（客户端要保存这个）
    opened_count: int = 0
    reset_count: int = 0

    # ---------------- 对外接口：喂入一个「已重组」的完整帧 ----------------
    def handle_frame(self, frame_hex: str) -> Optional[str]:
        frame_hex = frame_hex.upper()
        if not frame_hex.startswith(P.PROTOCOL_HEADER) or len(frame_hex) < 14:
            return None

        after_header = frame_hex[4:6]
        if after_header == "FF":
            return self._handle_clear(frame_hex)
        return self._handle_cipher(frame_hex)

    # ---------------- 明文帧 ----------------
    def _handle_clear(self, frame_hex: str) -> Optional[str]:
        cmd = frame_hex[6:8]
        length = int(frame_hex[8:12], 16)
        body = frame_hex[12:12 + length * 2]
        cksum = frame_hex[12 + length * 2:14 + length * 2]
        if P.compute_validate_code(frame_hex[:-2]).upper() != cksum.upper():
            raise MockLockError("明文帧校验和错误")

        if cmd == P.CMD_REQUEST_RANDOM_NUMBER:
            rnd = os.urandom(16)
            self.current_random_hex = rnd.hex().upper()
            self.current_random_frame = P.build_random_response(self.current_random_hex)
            return self.current_random_frame

        if cmd == P.CMD_RESET:
            self.reset_count += 1
            self.activation_key = ""          # 门锁 reset 后原 activationKey 作废
            return P.build_clear_frame(P.CMD_RESET, "00")

        if cmd == P.CMD_ENCRYPTED_LINK_ESTABLISH:
            # 【P0 真机校正】13 是明文帧，但它的**响应**是密文帧（用 activationKey 派生 key）
            return self._encrypted_ack(P.CMD_ENCRYPTED_LINK_ESTABLISH, "00")

        if cmd == P.CMD_BATTERY_LEVEL:
            # 真机 01 走密文帧；此分支仅为兼容「误发明文 01」的旧路径保留
            return self._encrypted_ack(P.CMD_BATTERY_LEVEL, body[:2] or "00")

        return None

    # ---------------- 密文帧 ----------------
    def _handle_cipher(self, frame_hex: str) -> Optional[str]:
        length = int(frame_hex[4:8], 16)
        cipher = frame_hex[8:8 + length * 2]
        cksum = frame_hex[8 + length * 2:10 + length * 2]
        if P.compute_validate_code(frame_hex[:-2]).upper() != cksum.upper():
            raise MockLockError("密文帧校验和错误")

        if not self.current_random_frame:
            raise MockLockError("会话未初始化：尚未下发随机数")
        if not self.activation_key:
            raise MockLockError("activationKey 为空（可能已被 reset）")

        key = P.xor(P.fetch_random_number(self.current_random_frame), self.activation_key)
        plain = P.aes_decrypt_hex(cipher, key)
        if plain is None or not plain.startswith("ff"):
            raise MockLockError("解密失败：activationKey 不匹配（门锁拒绝）")

        sign = plain[2:4].upper()   # AES 解密结果是小写 hex，常量是大写，统一后再比较

        if sign == P.CMD_ENCRYPTED_LINK_ESTABLISH:
            return self._encrypted_ack(sign, "00")

        if sign == P.CMD_UNLOCK:
            self.opened_count += 1
            if self.ack_mode == "clear":
                return P.UNLOCK_SUCCESS_ACK
            return self._encrypted_ack(sign, self.force_status or "00")

        if sign == P.CMD_ACTIVATE:
            return self._encrypted_ack(sign, self.force_status or "00")

        if sign == P.CMD_GENERATE_KEY:
            return self._encrypted_ack(sign, "00", key_override=P.DEFAULT_AES_KEY)

        if sign == P.CMD_BATTERY_LEVEL:
            return self._encrypted_ack(sign, "00")

        return None

    # ---------------- ack 构造 ----------------
    def _encrypted_ack(self, sign: str, status: str, key_override: Optional[str] = None) -> str:
        key = key_override or self.activation_key
        frame = P.build(sign, status, self.current_random_frame, key)
        if frame is None:
            raise MockLockError("构造 ack 失败")
        return frame


# ==========================================================================
# 客户端侧（模拟 BLE 链路）
# ==========================================================================

@dataclass
class SimulatedLink:
    """
    与真实 BLE 客户端同构的离线链路：
      write(frame)  → 按 20 字节切包 → 门锁逐包收 → 返回响应帧
                    → 按 20 字节切包模拟 notify → MessageAssembler 重组 → 200ms 边界
    真实 P2 只需替换 _write_packets / _read_message 的底层实现。
    """
    lock: MockLock
    clock: float = 0.0
    log: List[Tuple[float, str, str]] = field(default_factory=list)

    def __post_init__(self):
        self._assembler = P.MessageAssembler()

    def _tick(self, ms: float) -> None:
        self.clock += ms / 1000.0

    def _note(self, direction: str, payload: str) -> None:
        self.log.append((round(self.clock, 3), direction, payload))

    def write_and_receive(self, frame_hex: str, timeout_ms: int = 10000) -> Optional[str]:
        # 1) 发送：20 字节/包，包间 50ms
        for pkt in P.split_packets(frame_hex):
            self._note("TX", pkt.hex().upper())
            self._tick(P.WRITE_DELAY_MS)
        self._note("TX-FRAME", frame_hex)

        # 2) 门锁处理（整帧）
        resp = self.lock.handle_frame(frame_hex)
        if resp is None:
            return None
        self._note("RX-FRAME", resp)

        # 3) 模拟 notify：20 字节/包，最后触发 200ms 空闲 → 一条完整消息
        chunks = P.split_packets(resp)
        for i, c in enumerate(chunks):
            self._assembler.feed(c, self.clock)
            self._note("RX", c.hex().upper())
            self._tick(20.0)   # 分片间隔
        # 拉长到超过空闲阈值，令重组器判定消息结束
        elapsed = 0.0
        while not self._assembler.is_message_complete(self.clock) and elapsed < timeout_ms:
            self._tick(20.0)
            elapsed += 20.0
        return self._assembler.take()

    # ---------------- 完整开锁工作流 ----------------
    def unlock(self, activation_key: str) -> Tuple[bool, str]:
        try:
            # step 1: 请求随机数（明文帧）
            rnd_frame = self.write_and_receive(P.frame_request_random_number())
            if not rnd_frame:
                return False, "未收到随机数响应"
            random_hex = P.fetch_random_number(rnd_frame)
            self._note("INFO", f"randomNumber={random_hex}")

            # step 2: 建立加密链路（明文固定帧；响应为密文帧）
            est = P.frame_encrypted_link_establish(rnd_frame, activation_key)
            if not est:
                return False, "建链帧构造失败（activationKey 异常）"
            est_ack = self.write_and_receive(est)
            if not P.is_link_established(est_ack or "", rnd_frame, activation_key):
                return False, "建立加密链路失败：activationKey 不匹配"

            # step 3: 开锁（密文帧，content 为空）
            unl = P.frame_unlock(rnd_frame, activation_key)
            if not unl:
                return False, "开锁帧构造失败"
            unl_ack = self.write_and_receive(unl)
            if not unl_ack:
                return False, "开锁无响应"

            if unl_ack.upper().startswith(P.UNLOCK_SUCCESS_ACK):
                return True, "开锁成功（裸 ack 命中）"
            ok, detail = P.parse(unl_ack, rnd_frame, activation_key)
            if ok:
                return True, "开锁成功"
            return False, f"开锁失败：{detail}"
        except MockLockError as e:
            return False, f"门锁侧异常：{e}"
        except Exception as e:  # noqa: BLE001
            return False, f"异常：{e}"


def run_unlock_offline(activation_key: str, ack_mode: str = "encrypted",
                       force_status: Optional[str] = None,
                       lock_key: Optional[str] = None) -> Tuple[bool, str, SimulatedLink]:
    """
    一行跑通离线开锁仿真。
    activation_key = 客户端出示的密钥；lock_key = 门锁实际烧录的密钥（默认相同）。
    二者不同即模拟「拿错钥匙」。
    """
    lock = MockLock(activation_key=lock_key or activation_key,
                    ack_mode=ack_mode, force_status=force_status)
    link = SimulatedLink(lock=lock)
    ok, msg = link.unlock(activation_key)
    return ok, msg, link
