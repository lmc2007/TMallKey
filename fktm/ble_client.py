# -*- coding: utf-8 -*-
"""
ble_client.py — fkTM P2：真实 BLE 门锁客户端（PC 端，跨平台）

与 voc_protocol.py / mock_lock.py 严格同构：协议层不重复实现，
SimulatedLink 里每一步在真实链路上的落点都在这里。
差别只有底层传输：从「内存里互喂」换成 bleak 的 BLE GATT。

真机实测参数（P0 2026-10-11 抓包 + VocLockHelperV2 反编译双重确认）：
    Service UUID   = 00002760-08c2-11e1-9073-0e8ac72e1001
    write 特征      = properties 含 0x04(write-no-resp) 或 0x08(write)
    notify 特征     = properties 含 0x10(notify)
    CCCD           = 00002902-0000-1000-8000-00805f9b34fb（bleak start_notify 自动写）
    切包            = 20 字节/包，包间 20ms（末包后不再等待）
    消息边界        = **帧长精确判定**（明文帧 7+len / 密文帧 5+len，收满即返回），
                      判定失败时回退 60ms 空闲兜底 —— 替代 App 原有的 200ms 空闲等待
    设备名          = ~vTmallLock

提速要点（P4）：
    1) 帧长精确判定          取代 200ms 空闲判帧 → 三次收帧合计省 ~600ms
    2) 末包不再 sleep         再省 ~20ms
    3) 已知地址直连           跳过扫描
    4) Android 侧额外：连接复用 + requestConnectionPriority(HIGH) + 保活 60s

依赖：bleak>=0.22（本机 3.0.2 已验证导入）
"""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from typing import Callable, List, Optional, Tuple

import voc_protocol as P

try:
    from bleak import BleakClient, BleakScanner
    from bleak.backends.device import BLEDevice
    from bleak.exc import BleakError
    _HAS_BLEAK = True
except Exception:  # noqa: BLE001  允许在无蓝牙环境下被 import（仅用 dry-run）
    BleakClient = BleakScanner = BLEDevice = None  # type: ignore
    BleakError = Exception  # type: ignore
    _HAS_BLEAK = False

SERVICE_UUID = P.DEFAULT_SERVICE_UUID
DEVICE_NAME = P.VOC_LOCK_TAG


def ensure_bleak() -> None:
    if not _HAS_BLEAK:
        raise RuntimeError("未安装 bleak。请执行: pip install bleak")


# ==========================================================================
# 扫描
# ==========================================================================

@dataclass
class FoundLock:
    address: str
    name: str
    rssi: Optional[int] = None
    raw_adv: Optional[str] = None        # 广播原始 hex（若可得，用于解析真实 MAC）


async def scan_locks(timeout: float = 8.0,
                     name_filter: str = DEVICE_NAME) -> List[FoundLock]:
    """扫描附近的 VOC 门锁（按设备名过滤）。"""
    ensure_bleak()
    found = await BleakScanner.discover(timeout=timeout, return_adv=True)
    out: List[FoundLock] = []
    for addr, (dev, adv) in found.items():
        nm = dev.name or getattr(adv, "local_name", None) or ""
        if name_filter and name_filter.lower() not in nm.lower():
            continue
        raw = None
        try:
            md = getattr(adv, "manufacturer_data", None) or {}
            if md:
                k = next(iter(md))
                raw = "%04X%s" % (k, md[k].hex().upper())
        except Exception:  # noqa: BLE001
            pass
        out.append(FoundLock(address=addr, name=nm,
                             rssi=getattr(adv, "rssi", None), raw_adv=raw))
    out.sort(key=lambda x: (x.rssi if x.rssi is not None else -999), reverse=True)
    return out


async def find_lock(timeout: float = 8.0, address: Optional[str] = None) -> Optional[FoundLock]:
    """指定 address 时直接返回（不扫描，提速）；否则按名字扫描。"""
    if address:
        return FoundLock(address=address, name=DEVICE_NAME)
    locks = await scan_locks(timeout=timeout)
    return locks[0] if locks else None


# ==========================================================================
# 客户端
# ==========================================================================

@dataclass
class BleVocLock:
    address: str
    timeout_ms: int = 10000
    verbose: bool = True
    log: List[Tuple[float, str, str]] = field(default_factory=list)

    _client: object = None
    _write_char: object = None
    _notify_char: object = None
    _rx_buf: bytearray = field(default_factory=bytearray)
    _last_rx: float = 0.0
    _t0: float = 0.0

    # ---------------- 日志 ----------------
    def _note(self, direction: str, payload: str) -> None:
        ts = time.time() - self._t0 if self._t0 else 0.0
        self.log.append((round(ts, 3), direction, payload))
        if self.verbose:
            print(f"  t={ts:>6.3f}s  {direction:<10} {payload}")

    # ---------------- notify 回调 ----------------
    def _on_notify(self, _char, data: bytearray) -> None:
        if not data:
            return
        self._rx_buf.extend(data)
        self._last_rx = asyncio.get_event_loop().time()
        self._note("RX", bytes(data).hex().upper())

    async def _recv_message(self, timeout_ms: Optional[int] = None) -> Optional[str]:
        """
        累积 notify，**按帧长度精确判定整帧**（省去 App 那 200ms 空闲等待）；
        长度判定失败时回退到 MESSAGE_FALLBACK_IDLE_MS 空闲兜底。
        """
        ensure_bleak()
        timeout_ms = timeout_ms or self.timeout_ms
        loop = asyncio.get_event_loop()
        deadline = loop.time() + timeout_ms / 1000.0
        while True:
            now = loop.time()
            if self._rx_buf:
                buf = bytes(self._rx_buf)
                want = P.expected_frame_length(buf)
                if want is not None and len(buf) >= want:
                    del self._rx_buf[:want]          # 取走整帧，多余字节留给下一次
                    return buf[:want].hex().upper()
                if (now - self._last_rx) * 1000.0 >= P.MESSAGE_FALLBACK_IDLE_MS:
                    msg = bytes(self._rx_buf)
                    self._rx_buf.clear()
                    return msg.hex().upper()
            if now >= deadline:
                return None
            await asyncio.sleep(0.004)

    async def _send_frame(self, frame_hex: str) -> None:
        """按 20 字节切包发送，包间 WRITE_DELAY_MS（最后一包后不再等待）。"""
        data = bytes.fromhex(frame_hex)
        resp = "write" in (self._write_char.properties or [])
        pkts = [data[i:i + P.WRITE_CHUNK] for i in range(0, len(data), P.WRITE_CHUNK)]
        for i, chunk in enumerate(pkts):
            await self._client.write_gatt_char(self._write_char, chunk, response=resp)
            self._note("TX", chunk.hex().upper())
            if i < len(pkts) - 1:
                await asyncio.sleep(P.WRITE_DELAY_MS / 1000.0)

    async def _send_and_recv(self, frame_hex: str) -> Optional[str]:
        self._rx_buf.clear()
        await self._send_frame(frame_hex)
        return await self._recv_message()

    # ---------------- 连接 ----------------
    async def __aenter__(self) -> "BleVocLock":
        ensure_bleak()
        self._t0 = time.time()
        self._note("INFO", f"connecting to {self.address} ...")
        self._client = BleakClient(self.address, timeout=self.timeout_ms / 1000.0)
        await self._client.connect()
        if not self._client.is_connected:
            raise RuntimeError("BLE 连接失败")
        self._note("INFO", "connected; discovering services ...")

        svc = None
        for s in self._client.services:
            if s.uuid.lower() == SERVICE_UUID.lower():
                svc = s
                break
        if svc is None:
            uuids = [s.uuid for s in self._client.services]
            raise RuntimeError(f"未找到服务 {SERVICE_UUID}；设备实际服务: {uuids}")

        for c in svc.characteristics:
            props = [p.lower() for p in (c.properties or [])]
            if self._write_char is None and ("write" in props or "write-without-response" in props):
                self._write_char = c
            if self._notify_char is None and ("notify" in props or "indicate" in props):
                self._notify_char = c
        if self._write_char is None or self._notify_char is None:
            detail = [(c.uuid, c.properties) for c in svc.characteristics]
            raise RuntimeError(f"缺少 write/notify 特征: {detail}")

        self._note("INFO", f"write={self._write_char.uuid}  notify={self._notify_char.uuid}")
        await self._client.start_notify(self._notify_char, self._on_notify)
        self._note("INFO", "notify enabled (CCCD 已写入)")
        return self

    async def __aexit__(self, *_exc) -> None:
        try:
            if self._client is not None:
                if self._notify_char is not None:
                    try:
                        await self._client.stop_notify(self._notify_char)
                    except Exception:  # noqa: BLE001
                        pass
                await self._client.disconnect()
                self._note("INFO", "disconnected")
        except Exception:  # noqa: BLE001
            pass

    # ---------------- 业务流程（与 SimulatedLink.unlock 同构） ----------------
    async def unlock(self, activation_key: str) -> Tuple[bool, str]:
        try:
            # step 1: 请求随机数（明文固定帧）
            rnd_frame = await self._send_and_recv(P.frame_request_random_number())
            if not rnd_frame:
                return False, "未收到随机数响应"
            try:
                random_hex = P.fetch_random_number(rnd_frame)
            except ValueError as e:
                return False, f"随机数帧解析失败: {e} (原始: {rnd_frame})"
            self._note("INFO", f"randomNumber={random_hex}")
            self._note("INFO", f"actualAesKey={P.xor(random_hex, activation_key).upper()}")

            # step 2: 建立加密链路（明文固定帧，响应为密文）
            est_ack = await self._send_and_recv(P.frame_encrypted_link_establish())
            if not P.is_link_established(est_ack or "", rnd_frame, activation_key):
                return False, "建立加密链路失败：activationKey 不匹配"

            # step 3: 开锁（密文帧，content 为空）
            unl = P.frame_unlock(rnd_frame, activation_key)
            if not unl:
                return False, "开锁帧构造失败"
            unl_ack = await self._send_and_recv(unl)
            if not unl_ack:
                return False, "开锁无响应"
            if unl_ack.upper().startswith(P.UNLOCK_SUCCESS_ACK):
                return True, "开锁成功（裸 ack 命中）"
            ok, detail = P.parse(unl_ack, rnd_frame, activation_key)
            if ok:
                return True, "开锁成功"
            return False, f"开锁失败：{detail}"
        except BleakError as e:
            return False, f"BLE 异常: {e}"

    async def battery(self, activation_key: str) -> Tuple[bool, Optional[int], str]:
        try:
            rnd_frame = await self._send_and_recv(P.frame_request_random_number())
            if not rnd_frame:
                return False, None, "未收到随机数响应"
            random_hex = P.fetch_random_number(rnd_frame)
            est_ack = await self._send_and_recv(P.frame_encrypted_link_establish())
            if not P.is_link_established(est_ack or "", rnd_frame, activation_key):
                return False, None, "建立加密链路失败：activationKey 不匹配"
            ack = await self._send_and_recv(P.frame_battery(rnd_frame, activation_key))
            ok, detail = P.parse(ack or "", rnd_frame, activation_key)
            if not ok:
                return False, None, f"电量查询失败：{detail}"
            # 真机实测: ff 01 0004 00 64 ff 00 66 -> content[1] = 0x64 = 100%
            content = (detail or "")[8:8 + 4 * 2]
            pct = int(content[2:4], 16) if len(content) >= 4 else None
            return True, pct, f"电量 {pct}%  (content={content})"
        except Exception as e:  # noqa: BLE001
            return False, None, f"异常: {e}"


# ==========================================================================
# 便捷入口
# ==========================================================================

async def unlock_once(activation_key: str, address: Optional[str] = None,
                      scan_timeout: float = 8.0, verbose: bool = True) -> Tuple[bool, str]:
    found = await find_lock(timeout=scan_timeout, address=address)
    if not found:
        return False, f"未发现门锁（广播名应含 {DEVICE_NAME}）"
    if verbose:
        print(f"[*] 门锁: {found.name}  {found.address}"
              + (f"  RSSI={found.rssi}" if found.rssi is not None else ""))
    async with BleVocLock(address=found.address, verbose=verbose) as lock:
        return await lock.unlock(activation_key)
