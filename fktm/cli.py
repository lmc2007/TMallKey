# -*- coding: utf-8 -*-
"""
cli.py — fkTM P1 命令行工具

用法：
  python cli.py selftest                                  # 协议自检 + 离线开锁仿真 + 黄金向量
  python cli.py frames --key <activationKey> [--random <32hex>] [--mac XX:..]
                                                          # 打印 0D/13/0B/01 帧，供逐字节比对
  python cli.py simulate --key <activationKey> [--ack clear|encrypted] [--status 14]
  python cli.py verify --log p0_capture.txt               # 解析 P0 logcat 并逐字节对照
  python cli.py verify --selftest
  python cli.py scan [--timeout 8]                        # [P2] 扫描门锁
  python cli.py unlock [--key K] [--address A] [--dry-run]# [P2] 真实蓝牙开锁
  python cli.py battery [--key K]                          # [P2] 查询电量

约定：所有 activationKey 视为敏感凭证，仅本地使用，勿外传/勿上云。
"""

from __future__ import annotations

import argparse
import os
import sys

import voc_protocol as P
from logparse import parse_log, verify as verify_capture
from mock_lock import MockLock, SimulatedLink, run_unlock_offline

PASS, FAIL = "PASS", "FAIL"
_results = []


def _check(name: str, cond: bool, extra: str = "") -> None:
    _results.append((name, cond))
    mark = "[ ok ]" if cond else "[FAIL]"
    line = f"{mark} {name}"
    if extra:
        line += f"   {extra}"
    print(line)


# ==========================================================================
# 自检
# ==========================================================================

def _selftest_protocol() -> None:
    print("== 1. 固定帧校验（必须与 VocCommandType 硬编码常量逐字节一致） ==")
    _check("请求随机数 55AAFF0D00000B", P.frame_request_random_number() == "55AAFF0D00000B",
           P.frame_request_random_number())
    _check("建加密链路 55AAFF13000011", P.frame_encrypted_link_establish() == "55AAFF13000011",
           P.frame_encrypted_link_establish())
    _check("电量常量帧 55AAFF010000FF (真机未走此路)", P.frame_battery_fixed() == "55AAFF010000FF",
           P.frame_battery_fixed())
    _check("重置 55AAFF0C00000A", P.frame_reset() == "55AAFF0C00000A", P.frame_reset())
    _check("开锁成功裸 ack 55AAFF0B0001000A", P.UNLOCK_SUCCESS_ACK == "55AAFF0B0001000A")
    _check("build_clear_frame 复现上述常量", P.build_clear_frame("0B", "00") == P.UNLOCK_SUCCESS_ACK,
           P.build_clear_frame("0B", "00"))

    print("\n== 2. assembleDistributeContent（开锁明文应为 FF0B0000 + cksum + 补零至 32hex） ==")
    blob = P.assemble_distribute_content(P.CMD_UNLOCK, "")
    expect = ("FF" + "0B" + "0000" + "") + P.compute_validate_code("55AA" + "FF" + "0B" + "0000")
    expect = expect + "0" * ((-len(expect)) % 32)
    _check("开锁明文 == 期望", blob.upper() == expect.upper(), f"len={len(blob)} hex, {blob.upper()}")
    _check("开锁明文长度 = 32 hex (一个 AES 块)", len(blob) == 32)
    est = P.assemble_distribute_content(P.CMD_ENCRYPTED_LINK_ESTABLISH, "")
    _check("建链明文长度 = 32 hex", len(est) == 32, est.upper())
    ack = P.assemble_distribute_content(P.CMD_UNLOCK, "00")
    _check("ack 明文 [8:10] == 状态码位置", ack[8:10] == "00", ack.upper())

    print("\n== 3. AES-128/ECB 标准测试向量 (NIST FIPS-197) ==")
    kat = P.aes_encrypt_hex("00112233445566778899aabbccddeeff", "000102030405060708090a0b0c0d0e0f")
    _check("AES-128-ECB KAT", kat == "69c4e0d86a7b0430d8cdb78070b4c55a", kat)

    print("\n== 4. xor 密钥派生 ==")
    got = P.xor("00" * 16, "0F" * 16)
    _check("全 0 XOR 全 0F == 全 0F", got == "0f" * 16, got)

    print("\n== 5. 随机数提取 / MAC 逆序 ==")
    rnd = "0123456789ABCDEF0123456789ABCDEF"
    frame0d = P.build_random_response(rnd)
    _check("0D 响应帧长度 24 字节 (48hex)", len(frame0d) == 48, frame0d)
    _check("fetch_random_number 命中 [14:46]", P.fetch_random_number(frame0d) == rnd)
    try:
        P.fetch_random_number("55AA110D00000B")
        _check("非法帧应抛『随机数不匹配』", False)
    except ValueError as e:
        _check("非法帧抛『随机数不匹配』", str(e) == "随机数不匹配")
    _check("formatMacAddress 字节逆序", P.format_mac_address("112233445566") == "66:55:44:33:22:11",
           P.format_mac_address("112233445566"))
    _check("广播 MAC 解析 (FF + 偏移4 + 12hex)",
           P.extract_mac_from_broadcast("02AAFF00112233445566") == "66:55:44:33:22:11",
           P.extract_mac_from_broadcast("02AAFF00112233445566"))

    print("\n== 6. build / parse 往返 ==")
    key = "00112233445566778899AABBCCDDEEFF"
    for cmd in (P.CMD_ENCRYPTED_LINK_ESTABLISH, P.CMD_UNLOCK, P.CMD_ACTIVATE):
        f = P.build(cmd, "00", frame0d, key)
        ok, detail = P.parse(f, frame0d, key)
        _check(f"sign={cmd} 加解密往返", f.startswith("55AA") and ok, f"{f}  ->  {detail}")
    f = P.build(P.CMD_UNLOCK, "00", frame0d, key)
    bad_ok, _ = P.parse(f, frame0d, "FF" * 16)
    _check("错误 activationKey 必须解密失败/校验失败", not bad_ok)
    _check("错误 activationKey 会得到非 ff 开头明文（密钥丢失）", P.parse(f, frame0d, "FF" * 16)[1] is not None)

    print("\n== 7. 切包 ==")
    _check("24 字节帧 -> [20,4]", [len(x) for x in P.split_packets(frame0d)] == [20, 4],
           str([len(x) for x in P.split_packets(frame0d)]))
    _check("21 字节帧 -> [20,1]", [len(x) for x in P.split_packets("AA" * 21)] == [20, 1])

    print("\n== 8. 离线开锁仿真（端到端） ==")
    ok, msg, link = run_unlock_offline(key, ack_mode="encrypted")
    _check("密文 ack 开锁成功", ok, msg)
    _check("门锁计数 opened==1", link.lock.opened_count == 1, str(link.lock.opened_count))
    ok2, msg2, _ = run_unlock_offline(key, ack_mode="clear")
    _check("裸 ack 开锁成功", ok2, msg2)
    ok3, msg3, _ = run_unlock_offline("FF" * 16, ack_mode="encrypted", lock_key=key)
    _check("错误密钥必须开锁失败", not ok3, msg3)
    ok4, msg4, _ = run_unlock_offline(key, ack_mode="encrypted", force_status="14")
    _check("错误状态码映射为『锁体不适配』", (not ok4) and ("锁体不适配" in (msg4 or "")), msg4)

    print("\n== 9. 黄金向量（合成，逐字节锁定协议行为） ==")
    _selftest_golden_vectors()


# --- 黄金向量（合成，可公开）------------------------------------------------
# 为什么要合成而不是直接贴真机报文：
#   activationKey 就是这把门的「钥匙」，而 actualAesKey = randomNumber XOR activationKey，
#   两者同时出现在仓库里，等于把钥匙明文写进去 —— 任何人拿到就能离线开门。
#   所以公开仓库里只保留**假钥匙**（DEADBEEF…）现场合成的三组自洽会话，
#   断言同样逐字节锁定协议行为；真机报文留在本地取证文件中，不入库。
TEST_ACTIVATION_KEY = "DEADBEEFCAFEBABE0123456789ABCDEF"

TEST_S1_RND_FRAME = "55AAFF0D0011003F9A21C7D40B58E6A1C93D7B25F80E64AB"   # 会话1 UNLOCK
TEST_S1_AESKEY = "E1379F281EF5E258A0EA781CAC53C38B"
TEST_S1_13 = "55AAFF13000011"
TEST_S1_0B = "55AA0010C2A92FBC07CDE8CFF43D02BDCC4FC46C2B"
TEST_S1_13_ACK = "55AA0010D4E2E98F44E2F214EB60313F1DDAA703C5"
TEST_S1_0B_ACK = "55AA00102B6E4292BA050E28CF910BEA99867599F3"

TEST_S2_RND_FRAME = "55AAFF0D0011007C4E0B93A6F1D85230E7B4C91F6A2D58E7"   # 会话2 BATTERY
TEST_S2_AESKEY = "A2E3B57C6C0F62EC31C4F1AE96C1E0B7"
TEST_S2_01 = "55AA00109565EF5BE95123343CC3CDA8DD15F7DE1F"

# --- 会话3（独立随机数，用于验证同一把钥匙可跨会话复用）---------------------
TEST_S3_RND_FRAME = "55AAFF0D001100B5D1F0A824E67C3918BA4F2D60C9E7358C"   # 会话3 UNLOCK
TEST_S3_AESKEY = "6B7C4E47EE18C68719990A4AE9622ADA"
TEST_S3_0B = "55AA001000145A06C309B2ECA8D3EB3C6469487216"
TEST_S3_13_ACK = "55AA00108E2ED64A348AB2A4A23217410830656C34"
TEST_S3_0B_ACK = "55AA001056AC5D5EDB353C73F1816FE353A85741E2"


def _selftest_golden_vectors() -> None:
    # 1) 反解 activationKey：random XOR actualAesKey
    k1 = P.xor(P.fetch_random_number(TEST_S1_RND_FRAME), TEST_S1_AESKEY).upper()
    k2 = P.xor(P.fetch_random_number(TEST_S2_RND_FRAME), TEST_S2_AESKEY).upper()
    _check("会话1 反解 activationKey", k1 == TEST_ACTIVATION_KEY, k1)
    _check("会话2 反解 activationKey 与会话1 相同", k2 == TEST_ACTIVATION_KEY, k2)

    # 2) 派生 key 与实际打印一致
    _check("会话1 派生 actualAesKey == App 打印",
           P.xor(P.fetch_random_number(TEST_S1_RND_FRAME), TEST_ACTIVATION_KEY).upper() == TEST_S1_AESKEY)
    _check("会话2 派生 actualAesKey == App 打印",
           P.xor(P.fetch_random_number(TEST_S2_RND_FRAME), TEST_ACTIVATION_KEY).upper() == TEST_S2_AESKEY)

    # 3) 上行帧逐字节一致
    _check("会话1 13 建链 = 明文固定帧", P.frame_encrypted_link_establish() == TEST_S1_13, TEST_S1_13)
    _check("会话1 0B 开锁帧逐字节一致",
           P.frame_unlock(TEST_S1_RND_FRAME, TEST_ACTIVATION_KEY) == TEST_S1_0B,
           P.frame_unlock(TEST_S1_RND_FRAME, TEST_ACTIVATION_KEY))
    _check("会话2 01 电量帧逐字节一致",
           P.frame_battery(TEST_S2_RND_FRAME, TEST_ACTIVATION_KEY) == TEST_S2_01,
           P.frame_battery(TEST_S2_RND_FRAME, TEST_ACTIVATION_KEY))

    # 4) 上行 0B 密文解回明文：FF|0B|0000|cksum|补零
    plain = P.aes_decrypt_hex(P.extract_message(TEST_S1_0B), TEST_S1_AESKEY)
    _check("开锁明文 = FF|0B|0000|cksum|补零",
           (plain or "").upper() == "FF0B0000090000000000000000000000", plain)
    _check("0B cksum 字节 == 09 (= 55+AA+FF+0B mod 256)",
           (plain or "")[8:10].upper() == "09", (plain or "")[8:10])
    _check("开锁业务内容为空 (content 后全 0)",
           set((plain or "")[10:].lower()) <= {"0"}, (plain or "")[10:])

    # 5) 下行帧解密与 App 打印一致
    ok13, d13 = P.parse(TEST_S1_13_ACK, TEST_S1_RND_FRAME, TEST_ACTIVATION_KEY)
    _check("会话1 13 ack 解密 == ff1300010012...(状态00)",
           ok13 and d13.upper() == "FF130001001200000000000000000000", str(d13))
    ok0b, d0b = P.parse(TEST_S1_0B_ACK, TEST_S1_RND_FRAME, TEST_ACTIVATION_KEY)
    _check("会话1 0B ack 解密 == ff0b0001000a...(状态00)",
           ok0b and d0b.upper() == "FF0B0001000A00000000000000000000", str(d0b))

    # 6) 会话3（独立随机数，同一把钥匙跨会话复用）
    _check("会话3 反解 activationKey 与前两会话相同",
           P.xor(P.fetch_random_number(TEST_S3_RND_FRAME), TEST_S3_AESKEY).upper() == TEST_ACTIVATION_KEY)
    _check("会话3 派生 actualAesKey == 记录值",
           P.xor(P.fetch_random_number(TEST_S3_RND_FRAME), TEST_ACTIVATION_KEY).upper() == TEST_S3_AESKEY)
    _check("会话3 0B 开锁帧逐字节一致",
           P.frame_unlock(TEST_S3_RND_FRAME, TEST_ACTIVATION_KEY) == TEST_S3_0B,
           P.frame_unlock(TEST_S3_RND_FRAME, TEST_ACTIVATION_KEY))
    ok13b, d13b = P.parse(TEST_S3_13_ACK, TEST_S3_RND_FRAME, TEST_ACTIVATION_KEY)
    _check("会话3 13 ack 解密 状态码==00",
           ok13b and (d13b or "").upper()[8:10] == "00", str(d13b))
    ok0bb, d0bb = P.parse(TEST_S3_0B_ACK, TEST_S3_RND_FRAME, TEST_ACTIVATION_KEY)
    _check("会话3 0B ack 解密 状态码==00",
           ok0bb and (d0bb or "").upper()[8:10] == "00", str(d0bb))

    # 7) 帧长度精确判定 —— P4 提速核心：用帧头算长度，替代 200ms 空闲等待
    print("  -- 帧长度精确判定（提速：替代空闲 200ms 判帧）--")
    _check("0D 响应明文帧长判定 == 24 字节",
           P.expected_frame_length(bytes.fromhex(TEST_S1_RND_FRAME)) == 24,
           str(P.expected_frame_length(bytes.fromhex(TEST_S1_RND_FRAME))))
    _check("13 ack 密文帧长判定 == 21 字节",
           P.expected_frame_length(bytes.fromhex(TEST_S1_13_ACK)) == 21,
           str(P.expected_frame_length(bytes.fromhex(TEST_S1_13_ACK))))
    _check("0B ack 密文帧长判定 == 21 字节",
           P.expected_frame_length(bytes.fromhex(TEST_S1_0B_ACK)) == 21,
           str(P.expected_frame_length(bytes.fromhex(TEST_S1_0B_ACK))))
    all_frames = (TEST_S1_RND_FRAME, TEST_S2_RND_FRAME, TEST_S3_RND_FRAME,
                  TEST_S1_13, TEST_S1_13_ACK, TEST_S1_0B_ACK,
                  TEST_S2_01, TEST_S3_13_ACK, TEST_S3_0B_ACK, TEST_S3_0B)
    _check("帧长判定对全部 10 个向量帧都精确成立",
           all(P.expected_frame_length(bytes.fromhex(f)) * 2 == len(f) for f in all_frames),
           str([(f[:12], P.expected_frame_length(bytes.fromhex(f)), len(f) // 2) for f in all_frames
                if P.expected_frame_length(bytes.fromhex(f)) * 2 != len(f)]))
    _check("分片缓冲：只收 19 字节时不算完整帧",
           not P.frame_is_complete(bytes.fromhex(TEST_S1_RND_FRAME)[:19]))
    _check("分片缓冲：收满 24 字节判定完整帧",
           P.frame_is_complete(bytes.fromhex(TEST_S1_RND_FRAME)))
    _check("非法帧头 -> None", P.expected_frame_length(b"\x00\x01\x02\x03\x04\x05") is None)
    _check("数据不足 -> None", P.expected_frame_length(b"\x55\xAA") is None)


def cmd_selftest(_args) -> int:
    print("=" * 74)
    print(" fkTM P1 协议自检")
    print("=" * 74)
    _selftest_protocol()
    total = len(_results)
    passed = sum(1 for _, c in _results if c)
    print("\n" + "=" * 74)
    print(f" 结果: {passed}/{total} 通过" + ("" if passed == total else "   <-- 存在失败项"))
    print("=" * 74)
    return 0 if passed == total else 1


# ==========================================================================
# frames
# ==========================================================================

def cmd_frames(args) -> int:
    key = args.key
    mac = args.mac or "AA:BB:CC:DD:EE:FF"
    if args.random:
        rnd = args.random.upper()
        if len(rnd) != 32:
            print("--random 必须是 32 位 hex", file=sys.stderr)
            return 2
    else:
        rnd = os.urandom(16).hex().upper()
        print("(未提供 --random，本次使用随机数；P0 比对应传入抓包里的真实随机数)")

    frame0d = P.build_random_response(rnd)
    est = P.frame_encrypted_link_establish()
    unl = P.frame_unlock(frame0d, key)
    bat = P.frame_battery(frame0d, key)

    print()
    print(f"MAC                : {mac}")
    print(f"activationKey      : {key}")
    print(f"randomNumber(16B)  : {rnd}")
    print(f"actualAesKey       : {P.xor(rnd, key).upper()}   (= random XOR key末16字节)")
    print()
    print("--- 0D 请求随机数（明文固定帧，客户端发送）---")
    print(f"    {P.frame_request_random_number()}          ({len(P.frame_request_random_number())//2} 字节)")
    print("--- 0D 响应（门锁返回，明文帧；保存为 randomNumberProtocol）---")
    print(f"    {frame0d}   ({len(frame0d)//2} 字节)")
    print("--- 13 建立加密链路（明文固定帧，客户端发送；响应才是密文）---")
    print(f"    {est}")
    print("--- 0B 开锁（密文帧，客户端发送，明文内容为空）---")
    print(f"    {unl}")
    print("--- 01 电量（密文帧；真机实测走此路，非常量表里的明文帧）---")
    print(f"    {bat}")
    print()
    print("切包 (20 字节/包, 包间 50ms):")
    for name, fr in (("13", est), ("0B", unl), ("01", bat)):
        pkts = P.split_packets(fr)
        print(f"  [{name}] " + " | ".join(f"{i+1}:{p.hex().upper()}" for i, p in enumerate(pkts)))
    print()
    print("期望 ack: 55AAFF0B0001000A（裸）或解密后 ff..[8:10]==00（密文）")
    return 0


# ==========================================================================
# simulate
# ==========================================================================

def cmd_simulate(args) -> int:
    ok, msg, link = run_unlock_offline(args.key, ack_mode=args.ack, force_status=args.status)
    print(f"结果: {'成功' if ok else '失败'} — {msg}\n")
    print("时序日志:")
    for ts, direction, payload in link.log:
        print(f"  t={ts:>7.3f}s  {direction:<9} {payload}")
    print(f"\n门锁 opened_count={link.lock.opened_count}  reset_count={link.lock.reset_count}")
    return 0 if ok else 1


# ==========================================================================
# verify — P0 取证日志校验
# ==========================================================================

_MAC_FOR_SYNTH = "66:55:44:33:22:11"


def _synth_logcat(activation_key: str) -> str:
    """用离线仿真伪造一份与真机同格式的 logcat，用于自测 verify 链路。"""
    lock = MockLock(activation_key=activation_key)
    link = SimulatedLink(lock=lock)
    link.unlock(activation_key)

    lines = [
        f"10-11 17:00:00.100  9999  9999 D VocLockHelper: unlock: false "
        f"macAddress: {_MAC_FOR_SYNTH} activationKey: {activation_key}",
    ]
    rnd_hex = None
    for ts, direction, payload in link.log:
        stamp = f"10-11 17:00:00.{int(ts * 1000) % 1000:03d}"
        if direction == "TX":
            lines.append(f"{stamp}  9999  9999 D VocLockHelper: onCharacteristicWrite: "
                         f"status: 0 characteristic {payload} {len(payload) // 2}")
        elif direction == "RX":
            lines.append(f"{stamp}  9999  9999 D VocLockHelper: onCharacteristicChanged: "
                         f"value: {len(payload) // 2}  {payload}, com.tmall.campus.doorlock.VocLockHelper")
        elif direction == "INFO" and "randomNumber=" in payload:
            rnd_hex = payload.split("=", 1)[1]
    if rnd_hex:
        lines.append(f"10-11 17:00:01.000  9999  9999 D BluetoothHelper: parse: actualAesKey: "
                     f"{P.xor(rnd_hex, activation_key)}")
    return "\n".join(lines) + "\n"


def cmd_verify(args) -> int:
    if args.selftest:
        key = args.key or "0123456789ABCDEF0123456789ABCDEF"
        text = _synth_logcat(key)
        print("(--selftest: 使用离线仿真伪造的 logcat)\n")
    else:
        if not args.log:
            print("需要 --log <日志文件> 或 --selftest", file=sys.stderr)
            return 2
        with open(args.log, "r", encoding="utf-8", errors="ignore") as fh:
            text = fh.read()

    cap = parse_log(text)
    ok, report = verify_capture(cap)

    print("=" * 74)
    print(" fkTM P0 日志取证校验")
    print("=" * 74)
    for line in report:
        print(line)
    print("=" * 74)
    print(" 结论: " + ("协议复刻与真机逐字节一致" if ok else "存在不一致，需人工核对"))
    print("=" * 74)
    return 0 if ok else 1


# ==========================================================================
# P2 · 真实 BLE
# ==========================================================================

CRED_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "credentials.json")


def _load_credentials() -> dict:
    try:
        import json
        with open(CRED_FILE, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def _resolve_key(args) -> str:
    """优先 --key；否则从 credentials.json 取第一把锁的 activationKey。"""
    if getattr(args, "key", None):
        return args.key
    creds = _load_credentials()
    locks = creds.get("locks") or []
    if locks and locks[0].get("activationKey"):
        print(f"(* 使用 credentials.json 里的 {locks[0].get('name', '未命名')})")
        return locks[0]["activationKey"]
    print("需要 --key <activationKey>，或先在 credentials.json 里登记一把锁", file=sys.stderr)
    return ""


def _resolve_addr(args) -> str:
    if getattr(args, "address", None):
        return args.address
    creds = _load_credentials()
    locks = creds.get("locks") or []
    if locks and locks[0].get("address"):
        return locks[0]["address"]
    return ""


def _ble_hint(err: BaseException) -> str:
    s = str(err)
    if "No Bluetooth adapter" in s or "NO_BLUETOOTH" in s:
        return ("本机没有可用的蓝牙适配器（或已在系统里关闭）。\n"
                "  · 台式机：插一个 USB 蓝牙 dongle（BLE 4.0+），Windows 会自带驱动\n"
                "  · 笔记本：确认系统蓝牙已打开、且未被其他程序独占\n"
                "  · 本机无蓝牙时，P2 可先在手机上跑（把 fktm/ 拷过去用 termux + python）")
    if "Access Denied" in s or "0x80070005" in s:
        return "蓝牙被系统策略拒绝。检查 Windows 设置 → 隐私和安全性 → 蓝牙 是否允许应用访问。"
    return s


def cmd_scan(args) -> int:
    import asyncio
    from ble_client import scan_locks
    print(f"[*] 扫描 {args.timeout}s，按广播名过滤 '{P.VOC_LOCK_TAG}' ...")
    try:
        locks = asyncio.run(scan_locks(timeout=args.timeout))
    except RuntimeError as e:
        print(f"[XX] {e}", file=sys.stderr)
        return 2
    except Exception as e:  # noqa: BLE001
        print(f"[XX] 扫描失败: {_ble_hint(e)}", file=sys.stderr)
        return 2
    if not locks:
        print("[XX] 未发现门锁。检查：① 是否靠近门锁 ② 本机蓝牙是否打开 ③ App 是否正占用连接")
        return 1
    for l in locks:
        print(f"  {l.name:<16} {l.address}  RSSI={l.rssi}")
        if l.raw_adv:
            print(f"        adv = {l.raw_adv}")
    return 0


def cmd_ble_unlock(args) -> int:
    key = _resolve_key(args)
    if not key:
        return 2
    addr = _resolve_addr(args)

    if args.dry_run:
        print("(--dry-run: 不连接，只打印本次将下发的字节)")
        print(f"  activationKey : {key}")
        print(f"  MAC           : {addr or '(扫描)'}")
        print(f"  0D 请求       : {P.frame_request_random_number()}")
        print(f"  13 建链       : {P.frame_encrypted_link_establish()}")
        print("  (0B 开锁帧依赖门锁现场返回的随机数，无法预生成)")
        return 0

    import asyncio
    from ble_client import unlock_once
    print(f"[*] 目标: {addr or '(扫描中...)'}")
    try:
        ok, msg = asyncio.run(unlock_once(key, address=addr or None, scan_timeout=args.timeout))
    except RuntimeError as e:
        print(f"[XX] {e}", file=sys.stderr)
        return 2
    except Exception as e:  # noqa: BLE001
        print(f"[XX] 开锁失败: {_ble_hint(e)}", file=sys.stderr)
        return 2
    print(f"\n[{'OK' if ok else 'XX'}] {msg}")
    return 0 if ok else 1


def cmd_ble_battery(args) -> int:
    key = _resolve_key(args)
    if not key:
        return 2
    addr = _resolve_addr(args)
    import asyncio
    from ble_client import BleVocLock, find_lock

    async def _run():
        found = await find_lock(timeout=args.timeout, address=addr or None)
        if not found:
            return False, None, f"未发现门锁（广播名应含 {P.VOC_LOCK_TAG}）"
        async with BleVocLock(address=found.address) as lock:
            return await lock.battery(key)

    try:
        ok, pct, msg = asyncio.run(_run())
    except RuntimeError as e:
        print(f"[XX] {e}", file=sys.stderr)
        return 2
    except Exception as e:  # noqa: BLE001
        print(f"[XX] 查询失败: {_ble_hint(e)}", file=sys.stderr)
        return 2
    print(f"\n[{'OK' if ok else 'XX'}] {msg}")
    return 0 if ok else 1


def cmd_ble_hold(args) -> int:
    """保持一条 BLE 连接 N 秒 —— 用于**实测门锁的最大并发连接数**。

    这个值决定了「长时间驻留一条连接」是否会妨碍别人开门：
      · 若并发数 >= 2 → 占用一条影响不大；
      · 若并发数 == 1 → 你连着，别人（包括你自己另一台设备）就打不开。
    """
    addr = _resolve_addr(args)
    import asyncio
    from ble_client import BleVocLock, find_lock

    async def _run():
        found = await find_lock(timeout=args.timeout, address=addr or None)
        if not found:
            return False, f"未发现门锁（广播名应含 {P.VOC_LOCK_TAG}）"
        print(f"[OK] 已连上 {found.name} {found.address}，保持 {args.seconds:.0f}s ...")
        print("     现在请在手机上用 fkTM 开锁，然后看结果：")
        print("       · 手机也能开  → 门锁并发数 >= 2（驻留一条影响不大）")
        print("       · 手机开不了  → 门锁并发数 = 1（本连接独占，驻留会妨碍别人）")
        async with BleVocLock(address=found.address, verbose=args.verbose) as lock:  # noqa: F841
            await asyncio.sleep(args.seconds)
        return True, f"已释放连接（保持 {args.seconds:.0f}s 结束）"

    try:
        ok, msg = asyncio.run(_run())
    except KeyboardInterrupt:
        print("\n[--] 已手动中断，连接随之释放")
        return 0
    except RuntimeError as e:
        print(f"[XX] {e}", file=sys.stderr)
        return 2
    except Exception as e:  # noqa: BLE001
        print(f"[XX] 保持连接失败: {_ble_hint(e)}", file=sys.stderr)
        return 2
    print(f"\n[{'OK' if ok else 'XX'}] {msg}")
    return 0 if ok else 1


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="fktm", description="fkTM P1 协议工具")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("selftest", help="协议自检 + 离线开锁仿真").set_defaults(func=cmd_selftest)

    fp = sub.add_parser("frames", help="打印 0D/13/0B 三帧")
    fp.add_argument("--key", required=True, help="activationKey")
    fp.add_argument("--random", help="16 字节随机数 (32 hex)；不传则随机生成")
    fp.add_argument("--mac", help="门锁 MAC，仅用于展示")
    fp.set_defaults(func=cmd_frames)

    sp = sub.add_parser("simulate", help="离线开锁仿真（含时序日志）")
    sp.add_argument("--key", required=True, help="activationKey")
    sp.add_argument("--ack", choices=["clear", "encrypted"], default="encrypted")
    sp.add_argument("--status", help="强制门锁返回的错误状态码，如 14")
    sp.set_defaults(func=cmd_simulate)

    vp = sub.add_parser("verify", help="解析 P0 logcat 取证日志并与复刻帧逐字节对照")
    vp.add_argument("--log", help="logcat 输出文件")
    vp.add_argument("--selftest", action="store_true", help="用离线仿真伪造日志自测该链路")
    vp.add_argument("--key", help="selftest 用的 activationKey")
    vp.set_defaults(func=cmd_verify)

    # --- P2：真实 BLE ---
    cp = sub.add_parser("scan", help="[P2] 扫描附近的 VOC 门锁")
    cp.add_argument("--timeout", type=float, default=8.0)
    cp.set_defaults(func=cmd_scan)

    up = sub.add_parser("unlock", help="[P2] 真实蓝牙开锁")
    up.add_argument("--key", help="activationKey（缺省读 credentials.json）")
    up.add_argument("--address", help="门锁 BLE 地址（缺省读 credentials.json 或现场扫描）")
    up.add_argument("--timeout", type=float, default=8.0, help="扫描超时(秒)")
    up.add_argument("--dry-run", action="store_true", help="不连接，只打印将下发的帧")
    up.set_defaults(func=cmd_ble_unlock)

    bp = sub.add_parser("battery", help="[P2] 查询门锁电量")
    bp.add_argument("--key", help="activationKey（缺省读 credentials.json）")
    bp.add_argument("--address", help="门锁 BLE 地址")
    bp.add_argument("--timeout", type=float, default=8.0)
    bp.set_defaults(func=cmd_ble_battery)

    hp = sub.add_parser("hold", help="[P2] 保持一条连接，用于实测门锁最大并发连接数")
    hp.add_argument("--address", help="门锁 BLE 地址（缺省读 credentials.json 或现场扫描）")
    hp.add_argument("--timeout", type=float, default=8.0, help="扫描超时(秒)")
    hp.add_argument("--seconds", type=float, default=120.0, help="保持时长(秒)")
    hp.add_argument("--verbose", action="store_true", help="打印收发时序")
    hp.set_defaults(func=cmd_ble_hold)

    args = ap.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
