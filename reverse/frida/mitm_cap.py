#!/usr/bin/env python3
"""mitmproxy 抓包：把天猫校园的 mtop 请求（URL + 全部头 + body）落盘。

为什么用中间人而不是 hook TLS 函数：
  App 的 mtop 走阿里 tnet（libtnet.so）+ cronet，TLS 是静态链接的 BoringSSL，
  `SSL_write` 要么没导出、要么被内联，native hook 一个都挂不上。
  中间人代理在 TCP 层劫持，跟 App 用哪个网络库无关 —— 这是唯一稳定的做法。

用法:
    python mitm_cap.py            # 监听 8080，持续抓
落盘: D:/sgtmp/mitm/mtop_flows.txt
"""
from __future__ import annotations

import asyncio
import os
import sys

from mitmproxy import options
from mitmproxy.tools.dump import DumpMaster

OUT = "D:/sgtmp/mitm/mtop_flows.txt"
INTEREST = ("mloginService", "smsSend", "smsLogin", "member.user.login", "mtop")


class Cap:
    def __init__(self) -> None:
        self.n = 0
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        self.fh = open(OUT, "w", encoding="utf-8")

    def _keep(self, url: str) -> bool:
        return "mtop" in url or "confong" in url

    def request(self, flow) -> None:
        url = flow.request.pretty_url
        # 先全部打出来：用来判断 App 到底走不走系统代理
        print(f"[all] {flow.request.method} {url[:120]}", flush=True)
        if not self._keep(url):
            return
        self.n += 1
        body = flow.request.get_text(strict=False) or ""
        lines = [f"=== #{self.n} {flow.request.method} {url}"]
        for k, v in flow.request.headers.items():
            lines.append(f"{k}: {v}")
        lines.append("")
        lines.append(body[:4000])
        lines.append("")
        text = "\n".join(lines)
        self.fh.write(text + "\n")
        self.fh.flush()
        flag = " <<< SMS" if "mloginService" in url else ""
        print(f"[{self.n}] {flow.request.method} {url[:120]}{flag}", flush=True)

    def response(self, flow) -> None:
        url = flow.request.pretty_url
        if not self._keep(url):
            return
        try:
            txt = flow.response.get_text(strict=False) or ""
        except Exception:
            txt = ""
        if "mloginService" in url:
            print(f"    -> {txt[:300]}", flush=True)
            self.fh.write(f"--- RESP {url[:100]}\n{txt[:2000]}\n\n")
            self.fh.flush()


async def main() -> None:
    opts = options.Options(listen_port=8443)
    m = DumpMaster(opts, with_termlog=False, with_dumper=False)
    m.addons.add(Cap())
    print("[+] mitmproxy 监听 :8443", flush=True)
    await m.run()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
