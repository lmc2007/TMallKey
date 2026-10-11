# -*- coding: utf-8 -*-
"""
logparse.py — 解析天猫校园 logcat 取证日志，并与 P1 复刻帧逐字节对照 (fkTM P0)

为什么能做到「不需要 root」：
  VocLockHelper.unlock() 里有一行
      Log.d(TAG, "unlock: " + isWorking + " macAddress: " + mac + " activationKey: " + key);
  TAG = "VocLockHelper"，走的是 android.util.Log，任何 `adb logcat` 都能看到。
  BluetoothHelper.build/parse 还会打印派生出的 actualAesKey；
  onCharacteristicWrite / onCharacteristicChanged 会打印每一个收发字节。

  注意：App 自身的 Logger 默认 logFlag=1（仅 logcat），但配置项 `config_log_flag`
  可开到 MASK_SLS=2，届时同样的内容会走阿里云日志服务上报 → 见 fkTM-PLAN.md §6 隐私提醒。
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

import voc_protocol as P

# --- logcat 行匹配 -----------------------------------------------------------
RE_TX = re.compile(r"onCharacteristicWrite:.*?characteristic\s+([0-9a-fA-F]+)")
RE_RX = re.compile(r"onCharacteristicChanged:.*?value:\s*\d+\s+([0-9a-fA-F]+)")
RE_AESKEY = re.compile(r"(?:build|parse):\s*(?:actualAesKey:\s*)?([0-9a-fA-F]{32})")
RE_KEY = re.compile(r"activationKey[:=]\s*([0-9a-fA-F]{16,128})")
RE_MAC = re.compile(r"macAddress[:=]\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
RE_ADV = re.compile(r"advertisString[:=]\s*([0-9A-Fa-f]{8,})")
RE_ERR = re.compile(r"VocLockHelper.*?(?:error|failed|失败|超时)", re.IGNORECASE)


@dataclass
class Capture:
    activation_key: Optional[str] = None
    mac_address: Optional[str] = None
    advertis_string: Optional[str] = None
    actual_aes_keys: List[str] = field(default_factory=list)
    tx_frames: List[str] = field(default_factory=list)
    rx_frames: List[str] = field(default_factory=list)
    errors: List[str] = field(default_factory=list)


def _reassemble(packets: List[str]) -> List[str]:
    """把 20 字节分片的 write/notify 流还原成完整帧（自动识别明文/密文两种布局）。"""
    buf = ""
    out: List[str] = []
    for pkt in packets:
        buf += pkt.upper()
        while len(buf) >= 10:
            idx = buf.find("55AA")
            if idx < 0:
                buf = ""
                break
            if idx > 0:
                buf = buf[idx:]
            if len(buf) < 12:
                break
            if buf[4:6] == "FF":                       # 明文帧: 55AA|FF|cmd|len|payload|cksum
                n = int(buf[8:12], 16)
                total = 14 + n * 2
            else:                                      # 密文帧: 55AA|len|cipher|cksum
                n = int(buf[4:8], 16)
                total = 10 + n * 2
            if len(buf) < total:
                break
            out.append(buf[:total])
            buf = buf[total:]
    return out


def parse_log(text: str) -> Capture:
    cap = Capture()
    tx_pkts, rx_pkts = [], []
    for line in text.splitlines():
        m = RE_KEY.search(line)
        if m and not cap.activation_key:
            cap.activation_key = m.group(1).upper()
        m = RE_MAC.search(line)
        if m and not cap.mac_address:
            cap.mac_address = m.group(1).upper()
        m = RE_ADV.search(line)
        if m and not cap.advertis_string:
            cap.advertis_string = m.group(1).upper()
        for m in RE_AESKEY.finditer(line):
            if m.group(1).upper() not in cap.actual_aes_keys:
                cap.actual_aes_keys.append(m.group(1).upper())
        m = RE_TX.search(line)
        if m:
            tx_pkts.append(m.group(1))
        m = RE_RX.search(line)
        if m:
            rx_pkts.append(m.group(1))
        if RE_ERR.search(line) and line not in cap.errors:
            cap.errors.append(line.strip()[:200])
    cap.tx_frames = _reassemble(tx_pkts)
    cap.rx_frames = _reassemble(rx_pkts)
    return cap


def find_random_frame(cap: Capture) -> Optional[str]:
    """从捕获的 RX 里找 0D 响应帧（即 randomNumberProtocol）。"""
    for f in cap.rx_frames:
        try:
            P.fetch_random_number(f)
            return f
        except Exception:
            continue
    return None


def all_random_frames(cap: Capture) -> List[str]:
    """按时间顺序取出所有 0D 响应帧（一次开锁一个会话）。"""
    out = []
    for f in cap.rx_frames:
        try:
            P.fetch_random_number(f)
            out.append(f)
        except Exception:
            continue
    return out


def recover_activation_key(cap: Capture) -> Tuple[Optional[str], List[str]]:
    """
    【P0 真机校正 2026-10-11】即使日志里没打印 activationKey，也能反解出来。

    原理（密钥派生是可逆的）：
        actualAesKey = randomNumber XOR activationKey
    日志里同时存在 randomNumber（0D 响应帧明文携带）与 actualAesKey
    （BluetoothHelper.parse / VocUnlockBridge.build 打印），于是：
        activationKey = randomNumber XOR actualAesKey

    多组 (随机数, 派生key) 必须解出同一个值；不一致则说明配对错位，拒绝输出。
    """
    notes: List[str] = []
    rnds = [P.fetch_random_number(f) for f in all_random_frames(cap)]
    keys = list(cap.actual_aes_keys)
    if not rnds or not keys:
        return None, ["[x] 缺少随机数帧或派生 key，无法反解 activationKey"]
    n = min(len(rnds), len(keys))
    notes.append(f"  (随机数帧 {len(rnds)} 个 / 派生 key {len(keys)} 个，按出现顺序配对 {n} 组)")
    cands: List[str] = []
    for i in range(n):
        c = P.xor(rnds[i], keys[i]).upper()
        cands.append(c)
        notes.append(f"  随机[{i}] {rnds[i]}")
        notes.append(f"  XOR key[{i}] {keys[i]}")
        notes.append(f"  ==> activationKey = {c}")
    uniq = sorted(set(cands))
    if len(uniq) != 1:
        notes.append(f"  [!] 多组解不一致，拒绝下结论: {uniq}")
        return None, notes
    notes.append(f"  [ok] {n} 组独立会话解出同一个 activationKey —— 结果可信")
    return uniq[0], notes


def verify(cap: Capture) -> Tuple[bool, List[str]]:
    """
    用捕获到的 activationKey + randomNumber 重算各命令帧，
    与真机抓到的 TX 帧逐字节对照。全等 → 协议复刻正确。
    """
    report: List[str] = []
    ok = True

    recovered_notes: Optional[List[str]] = None
    if not cap.activation_key:
        rec, notes = recover_activation_key(cap)
        if rec:
            cap.activation_key = rec
            recovered_notes = notes

    report.append(f"activationKey : {cap.activation_key or '<未抓到>'}"
                  + ("   [由 (随机数 XOR 派生key) 反解得到]" if recovered_notes else ""))
    if recovered_notes:
        report.extend(recovered_notes)
    report.append(f"macAddress    : {cap.mac_address or '<未抓到>'}")
    report.append(f"advertisString: {cap.advertis_string or '<未抓到>'}")
    report.append(f"派生 AES key  : {', '.join(cap.actual_aes_keys) or '<未抓到>'}")
    report.append(f"TX 帧 {len(cap.tx_frames)} 个 / RX 帧 {len(cap.rx_frames)} 个")
    report.append("")

    if not cap.activation_key:
        return False, report + ["[x] 没抓到 activationKey，也无法反解 —— 确认 App 未关日志、过滤标签正确"]

    rnd_frames = all_random_frames(cap)
    if not rnd_frames:
        return False, report + ["[x] 没抓到 0D 随机数响应帧 —— 确认现场真的点过『开锁』"]

    # ---- 明文固定帧 ----
    report.append("固定明文帧核对 (上行):")
    for label, frame in (("0D 请求随机数", P.frame_request_random_number()),
                         ("13 建立加密链路", P.frame_encrypted_link_establish())):
        hit = frame in cap.tx_frames
        ok &= hit
        report.append(f"  [{'ok' if hit else 'XX'}] {label}: {frame}")

    # ---- 密文帧逐会话 ----
    report.append("")
    report.append("密文帧逐字节对照 (上行):")
    cipher_cmds = [(P.CMD_UNLOCK, "0B 开锁"),
                   (P.CMD_BATTERY_LEVEL, "01 电量"),
                   (P.CMD_ACTIVATE, "06 激活"),
                   (P.CMD_GENERATE_KEY, "0E 生成密钥")]
    matched_tx = set()
    for i, rnd_frame in enumerate(rnd_frames, 1):
        rnd = P.fetch_random_number(rnd_frame)
        derived = P.xor(rnd, cap.activation_key).upper()
        same = derived in cap.actual_aes_keys
        if cap.actual_aes_keys and not same:
            ok = False
        report.append(f"  [会话 {i}] randomNumber = {rnd}")
        report.append(f"           复算 actualAesKey = {derived}"
                      + ("   == App 打印 ✓" if same else "   [!] 与 App 打印不一致"))
        for cmd, label in cipher_cmds:
            expect = P.build(cmd, "", rnd_frame, cap.activation_key)
            if expect is None:
                continue
            if expect in cap.tx_frames:
                matched_tx.add(expect)
                report.append(f"           [ok] {label}")
                report.append(f"                复算 = {expect}")
    # 未匹配到的密文上行帧
    leftover = [f for f in cap.tx_frames if (not f.startswith("55AAFF")) and f not in matched_tx]
    if leftover:
        report.append("  未匹配的上行密文帧（需人工核对）:")
        for f in leftover:
            report.append(f"    {f}")
        ok = False

    # ---- 下行解密 ----
    report.append("")
    report.append("下行帧解密核对:")
    seen = 0
    for f in cap.rx_frames:
        if f.startswith("55AAFF"):
            continue
        res = None
        for rnd_frame in rnd_frames:
            r = P.parse(f, rnd_frame, cap.activation_key)
            if r[0]:
                res = r
                break
        seen += 1
        report.append(f"  {'解析OK' if (res and res[0]) else '解析FAIL'}: {f}  ->  "
                      f"{res[1] if res else '无法解密'}")
    if seen == 0:
        report.append("  (无密文下行帧)")

    report.append("")
    if cap.errors:
        report.append("日志中的错误行:")
        for e in cap.errors[:10]:
            report.append(f"  {e}")

    return ok, report
