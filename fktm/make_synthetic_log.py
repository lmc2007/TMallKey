# -*- coding: utf-8 -*-
"""
make_synthetic_log.py — 生成一份「真机同格式」的 logcat 假日志 (fkTM P0 链路自测)

目的：在真正去门口抓包之前，先把
    logcat 行格式 → logparse 正则 → 帧重组 → verify 逐字节对照
这整条链路跑通，确保下一次现场抓取一定能被解析。

注意：本脚本用 voc_protocol 生成帧，因此**只验证「日志格式/解析/重组/报告」链路**，
不重复验证密码学正确性（那由 cli.py selftest 的 NIST 向量与离线仿真负责）。

日志行严格复刻真机格式：
    D/VocLockHelperV2( 4516): unlock: macAddress: <mac> activationKey: <key>
    D/VocLockHelperV2( 4516): onCharacteristicWrite: status: 0 characteristic <hex-chunk> <len>
    D/VocLockHelperV2( 4516): onCharacteristicChanged: value: <len>  <hex-chunk> for session sess_1
    D/BluetoothHelper( 4516): parse: actualAesKey: <key>
    D/VocUnlockBridge( 4516): build: <key>
"""
from __future__ import annotations

import sys
from typing import List

import voc_protocol as P

TAG_V2 = "VocLockHelperV2"
TAG_BT = "BluetoothHelper"
TAG_UNLOCK_BRIDGE = "VocUnlockBridge"
PID = " 4516"


def _fmt_hms(t: float) -> str:
    m, s = divmod(t, 60.0)
    h, m = divmod(int(m), 60)
    return f"10-11 {17 + h:02d}:{int(m):02d}:{s:06.3f}"


def emit(lines: List[str], t: float, tag: str, msg: str) -> float:
    lines.append(f"{_fmt_hms(t)} D/{tag}({PID}): {msg}")
    return t + 0.001


def chunks_of(frame_hex: str) -> List[str]:
    return [c.hex().upper() for c in P.split_packets(frame_hex)]


def build_log(activation_key: str, random_hex: str, mac: str,
              adv: str = "0201061AFF4C000215FF112233445566778899AABBCCDDEEFF00") -> str:
    lines: List[str] = ["--------- beginning of main"]
    t = 0.0

    # --- unlock() 入口：打出 mac + activationKey ---
    t = emit(lines, t, TAG_V2, f"unlock: macAddress: {mac} activationKey: {activation_key}")

    # --- step1: 请求随机数 (明文帧 0D) ---
    rnd_frame = P.build_random_response(random_hex)
    for ch in chunks_of(P.frame_request_random_number()):
        t = emit(lines, t, TAG_V2,
                 f"onCharacteristicWrite: status: 0 characteristic {ch} {len(ch) // 2}")
    for ch in chunks_of(rnd_frame):
        t = emit(lines, t, TAG_V2,
                 f"onCharacteristicChanged: value: {len(ch) // 2}  {ch} for session sess_1")

    # 派生 key（App 在 build/parse 里各打一次）
    derived = P.xor(P.fetch_random_number(rnd_frame), activation_key).upper()

    # --- step2: 建立加密链路 (13 密文帧) ---
    est_tx = P.frame_encrypted_link_establish(rnd_frame, activation_key)
    assert est_tx, "建链帧构造失败"
    t = emit(lines, t, TAG_UNLOCK_BRIDGE, f"build: {derived}")
    for ch in chunks_of(est_tx):
        t = emit(lines, t, TAG_V2,
                 f"onCharacteristicWrite: status: 0 characteristic {ch} {len(ch) // 2}")

    est_rx = P.build(P.CMD_ENCRYPTED_LINK_ESTABLISH, "00", rnd_frame, activation_key)
    t = emit(lines, t, TAG_BT, f"parse: actualAesKey: {derived}")
    for ch in chunks_of(est_rx):
        t = emit(lines, t, TAG_V2,
                 f"onCharacteristicChanged: value: {len(ch) // 2}  {ch} for session sess_1")

    # --- step3: 开锁 (0B 密文帧, content 为空) ---
    unl_tx = P.frame_unlock(rnd_frame, activation_key)
    assert unl_tx, "开锁帧构造失败"
    t = emit(lines, t, TAG_UNLOCK_BRIDGE, f"build: {derived}")
    for ch in chunks_of(unl_tx):
        t = emit(lines, t, TAG_V2,
                 f"onCharacteristicWrite: status: 0 characteristic {ch} {len(ch) // 2}")

    # 门锁回裸 ack（真机 UNLOCK_SUCCESS_ACK）
    t = emit(lines, t, TAG_BT, f"parse: actualAesKey: {derived}")
    for ch in chunks_of(P.UNLOCK_SUCCESS_ACK):
        t = emit(lines, t, TAG_V2,
                 f"onCharacteristicChanged: value: {len(ch) // 2}  {ch} for session sess_1")

    t = emit(lines, t, TAG_V2, "writeNextCommand: currentCommand: UNLOCK_FINISH for session sess_1")
    return "\n".join(lines) + "\n"


def main() -> int:
    activation_key = "0123456789ABCDEF0123456789ABCDEF"
    random_hex = "112233445566778899AABBCCDDEEFF00"
    mac = "66:55:44:33:22:11"
    text = build_log(activation_key, random_hex, mac)
    out = "synthetic_p0_log.txt"
    with open(out, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"OK wrote {out}  ({len(text.splitlines())} lines)")
    print(f"   activationKey = {activation_key}")
    print(f"   randomNumber  = {random_hex}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
