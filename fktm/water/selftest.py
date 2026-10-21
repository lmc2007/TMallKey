# -*- coding: utf-8 -*-
"""
selftest.py — mtop 层离线自检（不发任何真实请求）

跑法：
    python fktm/water/selftest.py

覆盖：签名黄金向量、token 解析、URL 一致性、两次握手、错误分类、剥层。
黄金向量 #2 是 2026-10-20 线上实证用过的那个签名 —— 服务器认了它
（返回 FAIL_SYS_TOKEN_EMPTY 而非签名错误），所以它是可信锚点。
"""

from __future__ import annotations

import json
import sys
import urllib.parse

from mtop import (
    APP_KEY,
    Cookies,
    MtopClient,
    MtopError,
    dumps,
    is_success,
    parse_h5_token,
    sign,
    token_expire_ms,
    unwrap,
)

_passed = 0
_failed: list[str] = []


def check(name: str, cond: bool, detail: str = "") -> None:
    global _passed
    if cond:
        _passed += 1
    else:
        _failed.append(f"{name}{(' — ' + detail) if detail else ''}")


def eq(name: str, got, want) -> None:
    check(name, got == want, f"got={got!r} want={want!r}")


# --------------------------------------------------------------------------
# 1. 签名黄金向量
# --------------------------------------------------------------------------

def test_sign():
    eq(
        "sign/向量1",
        sign("deadbeef", "1700000000000", "12574478", '{"a":1}'),
        "80b4fdec96bdd208fe8ae15b69e9f7df",
    )
    # 线上实证值：无 token 调 device.count，服务器回「令牌为空」= 签名被接受
    eq(
        "sign/向量2(线上实证)",
        sign("", "1792501006485", "12574478", "{}"),
        "2a0fb82ac340ff9790f4cb0a4ffc5661",
    )
    # 空 token 不是 "null" 也不是 "None"
    check(
        "sign/空token用空串",
        sign("", "1", APP_KEY, "{}") != sign("null", "1", APP_KEY, "{}"),
    )
    # 大小写：输出必须小写 hex（网关区分大小写）
    h = sign("x", "1", APP_KEY, "{}")
    eq("sign/小写hex", h, h.lower())
    eq("sign/长度32", len(h), 32)


# --------------------------------------------------------------------------
# 2. token 解析
# --------------------------------------------------------------------------

def test_token():
    eq("token/标准格式", parse_h5_token("abc123_1699999999999"), "abc123")
    eq("token/多下划线取首段", parse_h5_token("a_b_c_1699"), "a")
    eq("token/空值", parse_h5_token(None), "")
    eq("token/空串", parse_h5_token(""), "")
    eq("token/无时间戳", parse_h5_token("onlytoken"), "onlytoken")
    eq("token/空白裁剪", parse_h5_token("  abc_123  "), "abc")

    eq("expire/正常", token_expire_ms("abc_1699999999999"), 1699999999999)
    eq("expire/无时间戳", token_expire_ms("onlytoken"), 0)
    eq("expire/非数字", token_expire_ms("abc_xyz"), 0)
    eq("expire/空", token_expire_ms(None), 0)


# --------------------------------------------------------------------------
# 3. URL 构造一致性（签名用的 data 必须与发出的同一个串）
# --------------------------------------------------------------------------

def test_build_url():
    c = MtopClient(Cookies({"_m_h5_tk": "tok_1700000000000"}))
    url, payload = c.build_url(
        "mtop.tmall.campus.share.applet.device.count", {"a": 1, "b": "中"}, t="1700000000000"
    )
    q = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
    eq("url/api", q["api"][0], "mtop.tmall.campus.share.applet.device.count")
    eq("url/appKey", q["appKey"][0], APP_KEY)
    eq("url/v", q["v"][0], "1.0")
    eq("url/t", q["t"][0], "1700000000000")
    eq("url/data回读一致", q["data"][0], payload)
    eq("url/sign按回读data算", q["sign"][0], sign("tok", "1700000000000", APP_KEY, payload))
    check("url/中文不转义成\\u", "中" in payload, payload)
    check("url/无空格", " " not in payload, payload)

    # 用显式 token 覆盖 cookie 里的
    url2, _ = c.build_url("x.y", {}, t="1", token="")
    q2 = urllib.parse.parse_qs(urllib.parse.urlparse(url2).query)
    eq("url/显式token覆盖", q2["sign"][0], sign("", "1", APP_KEY, "{}"))


def test_dumps():
    eq("dumps/紧凑", dumps({"a": 1, "b": [1, 2]}), '{"a":1,"b":[1,2]}')
    eq("dumps/中文", dumps({"k": "水"}), '{"k":"水"}')


# --------------------------------------------------------------------------
# 4. Cookie 吸收
# --------------------------------------------------------------------------

def test_cookies():
    jar = Cookies({})
    changed = jar.update_from_set_cookie([
        "_m_h5_tk=newtok_1700000000000; Path=/; Domain=.taobao.com; HttpOnly",
        "_m_h5_tk_enc=enc123; Path=/; Domain=.taobao.com",
    ])
    eq("cookie/更新数", sorted(changed), ["_m_h5_tk", "_m_h5_tk_enc"])
    eq("cookie/取token", jar.token, "newtok")
    # 同值不重复记账
    eq("cookie/同值不重记", jar.update_from_set_cookie(["_m_h5_tk=newtok_1700000000000"]), [])
    # 脏数据不炸
    eq("cookie/无等号忽略", jar.update_from_set_cookie(["garbage"]), [])
    # repr 不泄漏值
    check("cookie/repr不含值", "newtok" not in repr(jar), repr(jar))
    check("cookie/header成对", "cookie2=" not in jar.header())


# --------------------------------------------------------------------------
# 5. 错误分类
# --------------------------------------------------------------------------

def test_errors():
    e = MtopError(["FAIL_SYS_TOKEN_EMPTY::令牌为空"])
    eq("err/code", e.code, "FAIL_SYS_TOKEN_EMPTY")
    eq("err/msg", e.message, "令牌为空")
    check("err/可重试", e.retryable)

    check("err/EXOIRED拼写",
          MtopError(["FAIL_SYS_TOKEN_EXOIRED::令牌过期"]).retryable)

    deep = MtopError(["FAIL_SYS_UNAUTHORIZED_ENTRANCE::API访问入口未授权"])
    check("err/resume不可重试", not deep.retryable)
    check("err/apiNotFounded不可重试",
          not MtopError(["FAIL_SYS_API_NOT_FOUNDED::请求API不存在"]).retryable)

    eq("err/空ret", MtopError([]).code, "UNKNOWN")


# --------------------------------------------------------------------------
# 6. 两次握手（假 opener，不发网络）
# --------------------------------------------------------------------------

class _Resp:
    def __init__(self, body: str, set_cookies: list[str]):
        self._body = body.encode()
        self.headers = _Headers(set_cookies)

    def read(self):
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class _Headers:
    def __init__(self, cookies):
        self._c = cookies

    def get_all(self, name):
        return self._c if name.lower() == "set-cookie" else []


class _Opener:
    """按脚本回放响应，记录实际发了几次请求。"""

    def __init__(self, scripts: list[tuple[str, list[str]]]):
        self.scripts = list(scripts)
        self.calls: list[str] = []

    def open(self, req, timeout=None):
        self.calls.append(req.full_url)
        body, cks = self.scripts.pop(0)
        return _Resp(body, cks)


def test_handshake():
    # 第一次：令牌为空，同时下发了 _m_h5_tk → 第二次应当带上新 token 并成功
    op = _Opener([
        ('{"ret":["FAIL_SYS_TOKEN_EMPTY::令牌为空"],"data":{}}',
         ["_m_h5_tk=fresh_1792501006485; Path=/"]),
        ('{"ret":["SUCCESS::调用成功"],"data":{"data":{"count":3}}}', []),
    ])
    c = MtopClient(Cookies({}), max_retry=2)
    out = c.call("mtop.test.api", {}, opener=op)

    eq("握手/请求次数", len(op.calls), 2)
    check("握手/首调无token", "sign=" in op.calls[0])
    eq("握手/token已吸收", c.cookies.token, "fresh")
    # 第二次的 sign 必须用新 token
    q2 = urllib.parse.parse_qs(urllib.parse.urlparse(op.calls[1]).query)
    eq("握手/二调用新token", q2["sign"][0], sign("fresh", q2["t"][0], APP_KEY, q2["data"][0]))
    check("握手/成功", is_success(out))
    eq("握手/剥层", unwrap(out), {"count": 3})

    # 不可重试错误：一次就抛，不浪费请求
    op2 = _Opener([('{"ret":["FAIL_SYS_UNAUTHORIZED_ENTRANCE::API访问入口未授权"]}', [])])
    try:
        MtopClient(Cookies({}), max_retry=2).call("mtop.x", {}, opener=op2)
        check("握手/未授权应抛错", False)
    except MtopError as e:
        eq("握手/未授权code", e.code, "FAIL_SYS_UNAUTHORIZED_ENTRANCE")
        eq("握手/未授权只发1次", len(op2.calls), 1)

    # retry=False：即便 token 类错误也不重试
    op3 = _Opener([('{"ret":["FAIL_SYS_TOKEN_EMPTY::令牌为空"]}', [])])
    try:
        MtopClient(Cookies({}), max_retry=2).call("mtop.y", {}, retry=False, opener=op3)
        check("握手/retryFalse应抛错", False)
    except MtopError:
        eq("握手/retryFalse只发1次", len(op3.calls), 1)


def test_unwrap():
    eq("unwrap/两层", unwrap({"data": {"data": {"a": 1}}}), {"a": 1})
    eq("unwrap/一层", unwrap({"data": {"a": 1}}), {"a": 1})
    eq("unwrap/无data", unwrap({"a": 1}), {"a": 1})
    eq("unwrap/非dict", unwrap([1, 2]), [1, 2])


# --------------------------------------------------------------------------

def test_parse_qr():
    from api import parse_qr

    # 真机实测的那张码
    d = parse_qr("https://share.confong.cn/xh?id=tmxy869451084947543")
    eq("qr/device_code", d.device_code, "tmxy869451084947543")
    eq("qr/device_id", d.device_id, "869451084947543")
    check("qr/valid", d.valid)

    # 只有裸串
    eq("qr/裸码", parse_qr("tmxy869451084947543").device_id, "869451084947543")
    # 纯数字（部分机型扫出来不带前缀）
    eq("qr/纯数字", parse_qr("869451084947543").device_code, "tmxy869451084947543")
    # 带额外参数
    eq("qr/多参数",
       parse_qr("https://share.confong.cn/xh?isv=1&id=tmxy123456789012&x=2").device_id,
       "123456789012")
    # 无关内容
    eq("qr/无效", parse_qr("hello world").valid, False)
    eq("qr/空", parse_qr("").valid, False)


def main() -> int:
    for fn in (
        test_sign, test_token, test_build_url, test_dumps,
        test_cookies, test_errors, test_handshake, test_unwrap,
        test_parse_qr,
    ):
        fn()
    total = _passed + len(_failed)
    if _failed:
        print(f"FAIL {_passed}/{total}")
        for f in _failed:
            print("  x", f)
        return 1
    print(f"OK {_passed}/{total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
