# -*- coding: utf-8 -*-
"""
mtop.py — 淘宝 H5 网关客户端（fkTM 饮水模块 · P1 地基）

饮水业务全在 H5，控制接口走的是淘宝标准 mtop 网关。这一层只做三件事：
    · 算签名（纯函数，可离线单测）
    · 管 token（`_m_h5_tk` 的两次握手）
    · 发请求 / 归一化错误

不含任何业务参数 —— 业务在 api.py，凭证在 credentials（绝不入库）。

--------------------------------------------------------------------------
已实证（2026-10-20，无登录态直连 h5api.m.taobao.com）
--------------------------------------------------------------------------
    general.device.unlock       -> FAIL_SYS_SESSION_EXPIRED    接口存在，鉴权高
    device.water.start          -> FAIL_SYS_SESSION_EXPIRED    接口存在
    device.water.stop           -> FAIL_SYS_SESSION_EXPIRED    接口存在
    device.water.resume         -> FAIL_SYS_UNAUTHORIZED_ENTRANCE  ★ 本 appKey 未开放
    general.order.detail.get    -> FAIL_SYS_TOKEN_EMPTY        接口存在，普通鉴权
    (瞎编的 api)                -> FAIL_SYS_API_NOT_FOUNDED    探针对照组有效

结论：网关地址 / appKey / 接口名 / 签名格式全部正确，**唯一硬依赖是登录态**。
`water.resume` 走不通，别在它身上设计流程。

--------------------------------------------------------------------------
一次完整调用长什么样
--------------------------------------------------------------------------
    GET https://h5api.m.taobao.com/h5/{api}/{v}/
        ?jsv=2.7.2 & appKey=12574478 & t=<ms> & sign=<md5>
        & api= & v=1.0 & type=originaljson & dataType=json & data=<json>
    sign = md5(token & t & appKey & data)      token = _m_h5_tk 的 "_" 前段

⚠ 签名用的 data 必须和真正发出去的 data **是同一个字符串**
   （JSON dump 参数固定：无空格分隔、ensure_ascii=False，否则签名对不上）。
"""

from __future__ import annotations

import hashlib
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Mapping

# --------------------------------------------------------------------------
# 常量
# --------------------------------------------------------------------------

APP_KEY = "12574478"          # 淘宝 H5 通用 appKey，全站共用
GATEWAY = "https://h5api.m.taobao.com/h5/{api}/{ver}/"

TOKEN_COOKIE = "_m_h5_tk"
TOKEN_COOKIE_ENC = "_m_h5_tk_enc"

#: 需要重新握手的错误（token 空 / 过期 / 被踢）。命中就刷新 token 重试一次。
RET_TOKEN_RETRY = (
    "FAIL_SYS_TOKEN_EMPTY",
    "FAIL_SYS_TOKEN_EXOIRED",   # 淘宝拼写就是 EXOIRED，别「修正」它
    "FAIL_SYS_TOKEN_EXPIRED",
    "FAIL_SYS_SESSION_EXPIRED",
    "FAIL_SYS_ILLEGAL_ACCESS",
)

#: 永不重试的错误 —— 重试只会白费时间或加重风控
RET_FATAL = (
    "FAIL_SYS_API_NOT_FOUNDED",
    "FAIL_SYS_UNAUTHORIZED_ENTRANCE",
    "FAIL_SYS_USER_VALIDATE",
)


class MtopError(RuntimeError):
    """网关返回 ret 不是 SUCCESS。"""

    def __init__(self, ret: list[str], payload: dict | None = None):
        self.ret = ret
        self.code = ret[0].split("::")[0] if ret else "UNKNOWN"
        self.message = ret[0].split("::", 1)[1] if ret and "::" in ret[0] else ""
        self.payload = payload or {}
        super().__init__(f"{self.code}: {self.message}")

    @property
    def retryable(self) -> bool:
        return self.code in RET_TOKEN_RETRY


# --------------------------------------------------------------------------
# 签名 / token（纯函数，离线可测）
# --------------------------------------------------------------------------

def sign(token: str, t: str | int, app_key: str, data: str) -> str:
    """md5( token & t & appKey & data )，小写 hex。

    四个成分的空值就是空串，不要塞 "null" 或 "None"。
    """
    raw = f"{token}&{t}&{app_key}&{data}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def parse_h5_token(cookie_value: str | None) -> str:
    """从 `_m_h5_tk` 取值里剥出 token。

    `_m_h5_tk` 形如 `<token>_<毫秒时间戳>`，签名只用前半段。
    没有 cookie 或格式不对时返回空串（首调必然拿到 FAIL_SYS_TOKEN_EMPTY，
    这是 mtop 的正常「两次握手」第一步，不是 bug）。
    """
    if not cookie_value:
        return ""
    head = cookie_value.split("_", 1)[0].strip()
    return head


def token_expire_ms(cookie_value: str | None) -> int:
    """从 `_m_h5_tk` 里取过期时间戳（毫秒），拿不到返回 0。"""
    if not cookie_value or "_" not in cookie_value:
        return 0
    tail = cookie_value.split("_", 1)[1].strip()
    return int(tail) if tail.isdigit() else 0


def dumps(data: Any) -> str:
    """签名与发送共用的 JSON 序列化 —— 两边必须一模一样。"""
    return json.dumps(data, separators=(",", ":"), ensure_ascii=False)


# --------------------------------------------------------------------------
# Cookie 容器
# --------------------------------------------------------------------------

@dataclass
class Cookies:
    """只保存 mtop 必需的几项，不保存密码，不打印值。"""

    jar: dict[str, str] = field(default_factory=dict)

    def get(self, name: str) -> str | None:
        return self.jar.get(name)

    def header(self) -> str:
        return "; ".join(f"{k}={v}" for k, v in self.jar.items())

    def update_from_set_cookie(self, headers: list[str]) -> list[str]:
        """吸收响应里的 Set-Cookie，返回被更新的 cookie 名（便于打日志）。"""
        changed: list[str] = []
        for h in headers:
            first = h.split(";", 1)[0]
            if "=" not in first:
                continue
            k, v = first.split("=", 1)
            k = k.strip()
            if not k:
                continue
            if self.jar.get(k) != v:
                self.jar[k] = v
                changed.append(k)
        return changed

    @property
    def token(self) -> str:
        return parse_h5_token(self.get(TOKEN_COOKIE))

    def __repr__(self) -> str:               # 永不打印值
        return f"Cookies({len(self.jar)} 项: {sorted(self.jar)})"


# --------------------------------------------------------------------------
# 客户端
# --------------------------------------------------------------------------

@dataclass
class MtopClient:
    """最小可用的 mtop 客户端。

    用法：
        c = MtopClient(Cookies({"_m_h5_tk": "...", "cookie2": "...", ...}))
        data = c.call("mtop.tmall.campus.share.applet.device.count", {})
    """

    cookies: Cookies
    app_key: str = APP_KEY
    timeout: float = 12.0
    #: 允许重试次数（token 握手 + 过期刷新），0 = 不重试（单测用）
    max_retry: int = 2

    def build_url(self, api: str, data: Any, ver: str = "1.0",
                  t: str | None = None, token: str | None = None) -> tuple[str, str]:
        """构造 URL，返回 (url, 实际用的 data 字符串)。"""
        payload = dumps(data)
        ts = t or str(int(time.time() * 1000))
        tk = self.cookies.token if token is None else token
        query = urllib.parse.urlencode({
            "jsv": "2.7.2",
            "appKey": self.app_key,
            "t": ts,
            "sign": sign(tk, ts, self.app_key, payload),
            "api": api,
            "v": ver,
            "type": "originaljson",
            "dataType": "json",
            "data": payload,
        })
        return f"{GATEWAY.format(api=api, ver=ver)}?{query}", payload

    def call(self, api: str, data: Any = None, ver: str = "1.0",
             retry: bool | None = None, opener=None) -> dict:
        """调用一个 mtop 接口，成功返回原始响应 dict，失败抛 MtopError。"""
        if data is None:
            data = {}
        allow = self.max_retry if retry is None else (self.max_retry if retry else 0)
        last: MtopError | None = None

        for attempt in range(allow + 1):
            url, _ = self.build_url(api, data, ver)
            payload, set_cookies = self._http(url, opener=opener)
            self.cookies.update_from_set_cookie(set_cookies)

            ret = payload.get("ret") or []
            if ret and str(ret[0]).startswith("SUCCESS"):
                return payload

            err = MtopError([str(x) for x in ret], payload)
            last = err
            # 只有 token 类错误值得重试：服务端刚塞下来的新 token 还没用上
            if attempt < allow and err.retryable:
                continue
            raise err

        raise last or MtopError(["FAIL_SYS_UNKNOWN::未知失败"])

    # -- 传输 ------------------------------------------------------------

    def _http(self, url: str, opener=None) -> tuple[dict, list[str]]:
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
                          "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
            "Referer": "https://share.confong.cn/",
            "Cookie": self.cookies.header(),
            "Accept": "application/json",
        })
        op = opener or urllib.request.build_opener()
        try:
            with op.open(req, timeout=self.timeout) as r:
                body = r.read().decode("utf-8", "replace")
                set_cookies = r.headers.get_all("Set-Cookie") or []
        except urllib.error.HTTPError as e:       # 4xx/5xx 也带 JSON body
            body = e.read().decode("utf-8", "replace")
            set_cookies = e.headers.get_all("Set-Cookie") or []
        except urllib.error.URLError as e:
            raise MtopError([f"NETWORK_ERROR::{e.reason}"]) from e

        try:
            return json.loads(body), set_cookies
        except json.JSONDecodeError:
            raise MtopError(["INVALID_JSON::" + body[:200]])


# --------------------------------------------------------------------------
# 便捷函数
# --------------------------------------------------------------------------

def is_success(payload: Mapping[str, Any]) -> bool:
    ret = payload.get("ret") or []
    return bool(ret) and str(ret[0]).startswith("SUCCESS")


def unwrap(payload: Mapping[str, Any]) -> Any:
    """mtop 的业务数据常在 data.data 里，两层都可能缺，慢慢剥。"""
    cur: Any = payload
    for _ in range(2):
        if isinstance(cur, Mapping) and "data" in cur:
            cur = cur["data"]
    return cur
