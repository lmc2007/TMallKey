# -*- coding: utf-8 -*-
"""
cmcheck.py —— 校园（CM）会话的取用与验收

## 背景（2026-11-01 实测结论）

同一个账号在 WebView 里有**两条 `cookie2`**：

    .taobao.com   ← 淘宝登录态，CM 层不认
    .confong.cn   ← 校园（掌门神通）会话，**CM 身份只认这一条**

只带 confong 的 `cookie2` 一条，就能从 PC 直连 mtop 拿到校园档案、设备列表、
甚至 `guide.zmst.unlock.data.get` 的 encryptData。所以 fkTM 的会话导入必须
**保证 jar 里的 `cookie2` 是 confong 那条**，否则会表现成「用户未登录 / CM❌」。

## 用法

    # 从 WebView 的 cookie 库直接验收（只读，不改任何东西）
    python fktm/water/cmcheck.py --db ~/.tmp/ck/Cookies.db

    # 只给一条 cookie2 也能验
    python fktm/water/cmcheck.py --cookie2 <confong 的 cookie2>

    # 只看 CM 状态，不打业务接口
    python fktm/water/cmcheck.py --db ... --cm-only
"""
from __future__ import annotations

import argparse
import sqlite3
import sys
from typing import Iterable, Sequence

from mtop import Cookies, MtopClient, MtopError

#: 需要从 WebView 库里取的域（其余是别的业务，没必要碰）
DOMAINS = ("confong.cn", "taobao.com", "tmall.com", "tmallcampus.com")

#: 决定 CM 身份的域
CONFONG_DOMAIN = "confong.cn"

#: Chrome 的 expires_utc 是 1601-01-01 起的**微秒**，落成毫秒要减这个偏移
CHROME_EPOCH_OFFSET_MS = 11_644_473_600_000

API_CM_USER = "mtop.tmall.campus.member.app.user.get"
API_ZMST_UNLOCK = "mtop.tmall.campus.guide.zmst.unlock.data.get"
API_CARD_LIST = "mtop.tmall.campus.share.user.card.list"


# --------------------------------------------------------------------------
# 1. cookie 库 → jar
# --------------------------------------------------------------------------

def rows_from_db(path: str, now_ms: int | None = None) -> list[tuple[str, str, str]]:
    """只读打开 WebView 的 Cookies SQLite，返回 (host, name, value)，顺手丢掉过期的。"""
    import time

    if now_ms is None:
        now_ms = int(time.time() * 1000)
    out: list[tuple[str, str, str]] = []
    con = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        for host, name, value, exp_utc in con.execute(
                "select host_key, name, value, expires_utc from cookies"):
            if not value:
                continue
            if not any(host.lstrip(".").endswith(d) for d in DOMAINS):
                continue
            if exp_utc:
                exp_ms = exp_utc // 1000 - CHROME_EPOCH_OFFSET_MS
                if 0 < exp_ms < now_ms - 60_000:
                    continue
            out.append((host, name, value))
    finally:
        con.close()
    return out


def merge_cookie_rows(rows: Iterable[Sequence[str]],
                      confong_domain: str = CONFONG_DOMAIN) -> dict[str, str]:
    """多域 cookie 行 → 扁平 jar，**confong 域的 cookie2 最后写、一定赢**。

    这是本模块存在的理由：按域顺序直接 `jar[name] = value` 时，
    `.taobao.com` 排在后面就会把 confong 的 cookie2 覆盖掉。
    """
    jar: dict[str, str] = {}
    confong_cookie2: str | None = None
    for host, name, value in rows:
        if not value:
            continue
        if name == "cookie2" and host.lstrip(".").endswith(confong_domain):
            confong_cookie2 = value
        jar[name] = value
    if confong_cookie2 is not None:
        jar["cookie2"] = confong_cookie2
    return jar


def load_webview_jar(path: str) -> dict[str, str]:
    return merge_cookie_rows(rows_from_db(path))


def jar_of_cookie2(value: str) -> dict[str, str]:
    """最小凭据：只有 confong 的 cookie2 一条。"""
    return {"cookie2": value}


def has_cm_cookie(jar: dict[str, str]) -> bool:
    """粗略判据：jar 里是否带了 confong 的 cookie2。"""
    return bool(jar.get("cookie2"))


# --------------------------------------------------------------------------
# 2. 探测
# --------------------------------------------------------------------------

def cm_user(jar: dict[str, str]) -> dict:
    """CM 层用户档案。失败抛 MtopError。"""
    c = MtopClient(Cookies(dict(jar)), max_retry=2)
    payload = c.call(API_CM_USER, {})
    return payload.get("data") or {}


def cm_ok(jar: dict[str, str]) -> bool:
    try:
        cm_user(jar)
        return True
    except (MtopError, Exception):  # noqa: BLE001
        return False


def account_id(jar: dict[str, str]) -> str:
    return jar.get("munb") or jar.get("unb") or ""


def verify(jar: dict[str, str]) -> dict:
    """只读验收：CM 档案 + 设备列表 + MV 解锁数据（不解锁，只取 encryptData）。

    返回结构化的结果，不抛异常，方便直接打印。
    """
    result: dict[str, object] = {"account": account_id(jar), "cm": False}

    try:
        user = cm_user(jar)
        result["cm"] = True
        result["nick"] = user.get("campusNickName")
        result["phone"] = user.get("phone")
    except Exception as e:  # noqa: BLE001
        result["cm_error"] = str(e)
        return result

    c = MtopClient(Cookies(dict(jar)), max_retry=2)
    for key, api, data in (
        ("card", API_CARD_LIST,
         {"requestType": "QUERY_USER_CARD_LIST",
          "requestJson": '{"cardBizType":"USER_WATER_CARD"}'}),
        ("zmst", API_ZMST_UNLOCK, {"macId": "D2:01:7C:A1:D8:A6"}),
    ):
        try:
            payload = c.call(api, data)
            body = payload.get("data") or {}
            if key == "zmst":
                result["zmst"] = f"encryptData {len(str(body.get('encryptData', '')))} 字符"
                result["zmst_device"] = body.get("deviceName")
            else:
                resp = body.get("response") or {}
                n = len(resp.get("cardList") or []) if isinstance(resp, dict) else 0
                result["card"] = f"水卡 {n} 张"
        except Exception as e:  # noqa: BLE001
            result[key] = f"ERR {e}"
    return result


# --------------------------------------------------------------------------
# 3. CLI
# --------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="校园 CM 会话验收（只读）")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--db", help="WebView 的 Cookies SQLite 路径")
    src.add_argument("--cookie2", help="只给 confong 域的 cookie2")
    ap.add_argument("--cm-only", action="store_true", help="只验 CM，不打业务接口")
    a = ap.parse_args(argv)

    jar = load_webview_jar(a.db) if a.db else jar_of_cookie2(a.cookie2)
    print(f"[in ] jar={len(jar)} 项 account={account_id(jar) or '-'} "
          f"cookie2={'有' if has_cm_cookie(jar) else '无'}")

    if a.cm_only:
        ok = cm_ok(jar)
        print(f"[cm ] {'OK' if ok else 'FAIL'}")
        return 0 if ok else 1

    r = verify(jar)
    print(f"[cm ] {'OK' if r['cm'] else 'FAIL'}  昵称={r.get('nick','-')} 手机={r.get('phone','-')}")
    if not r["cm"]:
        print(f"      错误: {r.get('cm_error')}")
        return 1
    print(f"[card] {r.get('card')}")
    print(f"[mv ] {r.get('zmst')}  device={r.get('zmst_device','-')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
