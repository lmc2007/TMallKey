# -*- coding: utf-8 -*-
"""
api.py — 校园饮水业务接口（fkTM 饮水模块 · P1）

参数全部来自**真机 CDP 抓包**（2026-10-20），hook `lib.mtop.request`
录到的真实调用。每一条注明「已验证」还是「待验证」。

⚠⚠ 命名陷阱：这套系统里 `deviceId` / `deviceCode` / `resNo` 三个名字被混用，
   同一个值在两处叫不同名字、同一个名字在两处指向不同值。实测对照：

       二维码          tmxy869451084947543     ← 带 tmxy 前缀
       H5 的 deviceId  869451084947543         ← 去掉前缀 = 真设备 ID
       resNo           630102040               ← 资源号/水机编号，与设备 ID 无算术关系
       water.start 的 deviceId   630102040     ← 是 resNo，不是设备 ID！
       activity.list 的 deviceCode 630102040   ← 也是 resNo
       fund.* 的 deviceId       869451084947543 ← 才是真设备 ID

--------------------------------------------------------------------------
扫码 → 解锁 的完整链路（2026-10-20 实测，快速启动路径）
--------------------------------------------------------------------------
    设备列表  device.list   {requestType:USER_DEVICE_LIST,
                             requestJson:{pageNum,pageSize,buildingIds:[3336],
                                          deviceType:"DRINKING_MACHINE"}}
      ▼
    进入详情页（H5 一次性打 8 个接口）
      general.device.info        {isv:CAMPUS, businessType:WASH_AND_CARE,
                                  resNo:630102040, paymentChannel:TMXY_APP}
      activity.list.withdetail   {deviceCode:630102040, campusAreaCode:500960,
                                  relationShipId:10915, washDeviceType:DRINKING_MACHINE}
      fund.use.device.info.get   {campusAreaCode:500960, payeeId:14735,
                                  relationShipId:10915, deviceId:869451084947543, ...}
      fund.device.pre.start.info.get  {campusAreaCode, deviceId, paymentChannel}
                                  → 返回 {} 空对象 = 免费、无需预授权
      water.coupon.list.get      ← 有没有可用券
      timelimit.discount.activity.query  {imei:869451084947543}   ← 字段名叫 imei，塞的是 deviceId
      device.user.activity.get   {deviceId:869451084947543}
      ▼
    ★ 解锁 = 只调一个接口，不需要 bizOrderId：
      device.water.start   {deviceId:"630102040",
                            paymentChannel:"TMXY_APP",
                            deviceConsumeSourceStr:"FAST_START"}
      ▲ 扁平参数（不套 requestType/requestJson）；deviceId 用 resNo
      ▼
    服务端建单，响应下发 isvOrderId / shareServiceInfoId
      （本次：shareServiceInfoId=353064725, isvOrderId=360584003）
      ▼
    页面变成「请在设备上操作出水/暂停」→ 出水靠机器上的物理按键，
    人不动它，超时按 0 元自动结单。

⚠ **`device.water.resume` 不可用**：线上实测 `FAIL_SYS_UNAUTHORIZED_ENTRANCE`，
  本 appKey 没有这个入口。暂停后只能 stop 再 start。

⚠ **本轮没有观察到 `general.device.unlock` 被调用** —— 饮水走快速启动/扫码时，
  `water.start` 本身就把锁开了。unlock 可能是洗护（洗衣机）那条链路的。
  保留该方法但标记为「未验证」。
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import parse_qs, urlparse

from mtop import Cookies, MtopClient, MtopError

# --------------------------------------------------------------------------
# 常量（全部来自抓包，别改）
# --------------------------------------------------------------------------

ISV = "CAMPUS"                       # ← 盲猜 1/2/3/TMXY 全错，真值是这个
BUSINESS_TYPE = "WASH_AND_CARE"      # 洗护与饮水共用
PAYMENT_CHANNEL = "TMXY_APP"
DEVICE_DRINKING = "DRINKING_MACHINE"

#: 本次实测的本校常量。换校区要重抓。
CAMPUS_AREA_CODE = 500960
BUILDING_ID = 3336                   # 仰山公寓 A
RELATIONSHIP_ID = 10915              # 支付/关系维度
PAYEE_ID = 14735
MAX_OUT_WATER_AMOUNT = 200           # ml

API_ACTIVITY = "mtop.tmall.campus.share.applet.activity.list.withdetail"
API_DEVICE_INFO = "mtop.tmall.campus.share.applet.general.device.info"
API_FUND_DEVICE_INFO = "mtop.tmall.campus.share.app.fund.use.device.info.get"
API_PRE_START = "mtop.tmall.campus.share.app.fund.device.pre.start.info.get"
API_COUPON_LIST = "mtop.tmall.campus.share.water.coupon.list.get"
API_USER_ACTIVITY = "mtop.tmall.campus.device.user.activity.get"
API_ORDER_DETAIL = "mtop.tmall.campus.share.applet.general.order.detail.get"
API_ORDER_STATUS = "mtop.tmall.campus.share.applet.general.order.status.get"
API_UNLOCK = "mtop.tmall.campus.share.applet.general.device.unlock"
API_WATER_START = "mtop.tmall.campus.share.applet.device.water.start"
API_WATER_STOP = "mtop.tmall.campus.share.applet.device.water.stop"
API_DEVICE_LIST = "mtop.tmall.campus.share.applet.device.list"

#: 扫码串里 deviceCode 的形态：tmxy + 15 位数字（实测 tmxy869451084947543）
RE_DEVICE_CODE = re.compile(r"\btmxy(\d{10,20})\b")


# --------------------------------------------------------------------------
# 扫码解析
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class ScannedDevice:
    """一张二维码解析出来的东西。"""

    raw: str
    device_code: str          # tmxy869451084947543
    device_id: str            # 869451084947543

    @property
    def valid(self) -> bool:
        return bool(self.device_id)


def parse_qr(text: str) -> ScannedDevice:
    """解析饮水机二维码。

    实测原文：
        https://share.confong.cn/xh?id=tmxy869451084947543

    `/xh` 只是个静态引导页（提示「请使用天猫校园APP扫描二维码」），
    真正的解析在 App 内。所以只要认出 `id=` 或裸 `tmxy...` 就够了。

    `deviceId` = `deviceCode` 去掉 `tmxy` 前缀 —— 这条是抓包验证的关键映射。
    """
    raw = (text or "").strip()
    code = ""

    # 1) 优先按 URL 的 id 参数取
    if "://" in raw or "?" in raw:
        try:
            q = parse_qs(urlparse(raw).query)
            cand = (q.get("id") or q.get("deviceCode") or [""])[0]
            if cand:
                code = cand
        except ValueError:
            pass

    # 2) 兜底：正则从整串里捞 tmxy<数字>；再不济整串就是纯数字
    if not code:
        m = RE_DEVICE_CODE.search(raw)
        if m:
            code = "tmxy" + m.group(1)
        elif raw.isdigit():
            code = raw

    did = ""
    if code.startswith("tmxy"):
        did = code[4:]
    elif code.isdigit():
        did = code
        code = "tmxy" + code

    return ScannedDevice(raw=raw, device_code=code, device_id=did)


# --------------------------------------------------------------------------
# 业务封装
# --------------------------------------------------------------------------

class WaterApi:
    """饮水业务接口。

    只读方法随便调；`unlock` / `start` / `stop` 会**真的让设备出水**，
    所以它们要求显式 `confirm=True`。

    典型用法：
        api = WaterApi(MtopClient(Cookies(jar)))
        d = parse_qr("https://share.confong.cn/xh?id=tmxy...")
        print(api.activities(d.device_code))          # 有没有免费额度
        print(api.pre_start(500960, d.device_id))     # {} = 免费
    """

    def __init__(self, client: MtopClient, campus_area_code: int | str = 0):
        self.c = client
        #: 用户所在校区，抓包里是 500960。可以用 set_campus 换。
        self.campus_area_code = campus_area_code

    # -- 内部 ----------------------------------------------------------

    def _req(self, api: str, request_type: str, payload: dict) -> dict:
        """`{requestType, requestJson}` 包装式。

        ⚠ 实测**只有少数老接口**是这种：device.list / water.coupon.list.get /
        customer.pop.list。别看到 mtop 就以为都要包一层。
        """
        r = self.c.call(api, {
            "requestType": request_type,
            "requestJson": json.dumps(payload, separators=(",", ":"), ensure_ascii=False),
        })
        data = r.get("data")
        return data if isinstance(data, dict) else {}

    def _flat(self, api: str, payload: dict) -> dict:
        """参数直接平铺 —— **饮水链路里绝大多数接口都是这种**。

        实测扁平的有：general.device.info、activity.list.withdetail、
        fund.use.device.info.get、fund.device.pre.start.info.get、
        device.water.start、device.user.activity.get、
        timelimit.discount.activity.query。

        ⚠ 给 `fund.device.pre.start.info.get` 套 requestType 包装会报
        `FAIL_SYS_BIZPARAM_MISSED`（说缺参数，其实是被包装吃掉了）。
        同一套系统里两种入参风格混着用，别想当然统一。
        """
        r = self.c.call(api, payload)
        data = r.get("data")
        return data if isinstance(data, dict) else {}

    # -- 只读 ----------------------------------------------------------

    def device_list(self, building_id: int | str = BUILDING_ID,
                    page_size: int = 10) -> dict:
        """按楼栋拉设备列表（包装式）。`buildingId=3336` = 仰山公寓 A。"""
        return self._req(API_DEVICE_LIST, "USER_DEVICE_LIST", {
            "pageNum": 1,
            "pageSize": page_size,
            "buildingIds": [int(building_id)],
            "deviceType": DEVICE_DRINKING,
        })

    def device_info(self, res_no: str) -> dict:
        """设备基础信息（扁平）。入参 `resNo` = 水机编号，如 630102040。"""
        return self._flat(API_DEVICE_INFO, {
            "isv": ISV,
            "businessType": BUSINESS_TYPE,
            "resNo": str(res_no),
            "paymentChannel": PAYMENT_CHANNEL,
        })

    def activities(self, res_no: str,
                   campus_area_code: int | str = CAMPUS_AREA_CODE,
                   relationship_id: int | str = RELATIONSHIP_ID,
                   device_type: str = DEVICE_DRINKING) -> dict:
        """设备可用的活动/优惠（免费水的来源）。

        ⚠ 实测是**扁平参数**，且这里的 `deviceCode` 填的是 **resNo**（630102040），
        不是二维码里的 `tmxy...`。已验证返回有数据。
        """
        return self._flat(API_ACTIVITY, {
            "deviceCode": str(res_no),
            "campusAreaCode": int(campus_area_code),
            "relationShipId": int(relationship_id),
            "washDeviceType": device_type,
        })

    def fund_device_info(self, device_id: str,
                         campus_area_code: int | str = CAMPUS_AREA_CODE,
                         relationship_id: int | str = RELATIONSHIP_ID,
                         payee_id: int | str = PAYEE_ID) -> dict:
        """支付侧的设备信息。这里的 `deviceId` 是**真设备 ID**（869451084947543）。"""
        return self._flat(API_FUND_DEVICE_INFO, {
            "campusAreaCode": int(campus_area_code),
            "businessTypeEnum": BUSINESS_TYPE,
            "payeeId": int(payee_id),
            "relationShipId": int(relationship_id),
            "deviceId": str(device_id),
            "paymentChannel": PAYMENT_CHANNEL,
        })

    def pre_start(self, device_id: str,
                  campus_area_code: int | str = CAMPUS_AREA_CODE) -> dict:
        """出水前的预授权/计价信息。

        返回 `{}` 空对象 = 免费、不需要预授权（本校就是这种情况）。
        ⚠ 扁平参数，`deviceId` 用**真设备 ID**。
        """
        return self._flat(API_PRE_START, {
            "campusAreaCode": int(campus_area_code),
            "deviceId": str(device_id),
            "paymentChannel": PAYMENT_CHANNEL,
        })

    def coupon_list(self, res_no: str, device_id: str,
                    campus_area_code: int | str = CAMPUS_AREA_CODE) -> dict:
        """可用优惠券（包装式）。"""
        return self._req(API_COUPON_LIST, "WATER_COUPON_INSTANCE_LIST", {
            "businessType": BUSINESS_TYPE,
            "orderTypeCode": "SERVICE_ORDER",
            "resNo": str(res_no),
            "deviceId": str(device_id),
            "campusAreaCode": int(campus_area_code),
            "paymentChannel": PAYMENT_CHANNEL,
            "deviceType": DEVICE_DRINKING,
            "isv": ISV,
            "maxOutWaterAmount": MAX_OUT_WATER_AMOUNT,
        })

    def user_activity(self, device_id: str, service_id: int | str = 0,
                      isv_order_id: str = "") -> dict:
        """我在这台设备上的使用记录 / 当前订单（扁平）。"""
        p = {"deviceId": str(device_id)}
        if service_id:
            p["serviceId"] = int(service_id)
        if isv_order_id:
            p["isvOrderId"] = str(isv_order_id)
        return self._flat(API_USER_ACTIVITY, p)

    def order_detail(self, device_code: str) -> dict:
        """查这台设备上我的订单。没有订单时返回 code=1048「订单未找到」。"""
        return self._req(API_ORDER_DETAIL, "ORDER_DETAIL_GET", {
            "isvResNo": device_code,
            "isv": ISV,
            "businessType": BUSINESS_TYPE,
        })

    def order_status(self, order: dict) -> dict:
        """轮询订单状态。"""
        return self._req(API_ORDER_STATUS, "ORDER_STATUS_GET", order)

    # -- 会出水，需要显式确认 -------------------------------------------

    def unlock(self, device: ScannedDevice, order: dict, confirm: bool = False) -> dict:
        """启动设备（洗护链路的用法）。

        ⚠ **本轮饮水全流程没有观察到这个接口被调用** —— 饮水走「快速启动/扫码」时，
        `water.start` 自己就把锁开了。下面的入参来自 JS 源码，未经真机验证。
        ⚠ 会真的解锁设备。未传 confirm=True 直接抛错。
        """
        if not confirm:
            raise PermissionError("unlock 会让设备进入可用状态，必须 confirm=True")
        return self._req(API_UNLOCK, "DEVICE_UNLOCK", {
            "isv": order.get("isv") or ISV,
            "businessType": order.get("businessType") or BUSINESS_TYPE,
            "deviceId": device.device_id,
            "bizOrderId": order.get("bizOrderIdStr") or order.get("bizOrderId") or "",
            "isvOrderId": order.get("isvOrderId") or "",
            "resNo": order.get("isvResNo") or device.device_code,
            "paymentChannel": order.get("paymentChannel") or PAYMENT_CHANNEL,
        })

    def start(self, res_no: str, source: str = "FAST_START",
              confirm: bool = False) -> dict:
        """解锁设备 = 服务端建单。⚠ 会让设备进入可用状态。

        2026-10-20 实测原始报文（**已验证**）：

            device.water.start
            data = {"deviceId": "630102040",
                    "paymentChannel": "TMXY_APP",
                    "deviceConsumeSourceStr": "FAST_START"}

        三个要点：
        1. **扁平参数**，不套 requestType/requestJson；
        2. `deviceId` 填的是 **resNo**（630102040），不是真设备 ID（869451084947543）；
        3. 不需要 `bizOrderId` —— 订单由服务端在本次调用里创建，
           响应里下发 `isvOrderId`（本次 = 360584003）。

        `source` 目前只见过 `FAST_START`（设备列表的「快速启动」）。
        扫码入口可能是别的值，抓到后补。

        解锁后**不会自动出水**，得在机器上按物理按键；不按就超时按 0 元结单。
        """
        if not confirm:
            raise PermissionError("water.start 会真的解锁设备，必须 confirm=True")
        return self._flat(API_WATER_START, {
            "deviceId": str(res_no),
            "paymentChannel": PAYMENT_CHANNEL,
            "deviceConsumeSourceStr": source,
        })

    def stop(self, res_no: str, confirm: bool = False) -> dict:
        """结束出水并结算。⚠ 会真的停水。

        ⚠ **未验证**：本轮没有走到 stop（解锁后人工未按键，超时 0 元自动结单）。
        这里按同族接口的扁平风格拼，字段以后续抓包为准。
        """
        if not confirm:
            raise PermissionError("water.stop 会真的停水，必须 confirm=True")
        return self._flat(API_WATER_STOP, {
            "deviceId": str(res_no),
            "paymentChannel": PAYMENT_CHANNEL,
        })


# --------------------------------------------------------------------------
# 结果判读
# --------------------------------------------------------------------------

#: 业务码 → 人话。都是抓包/探针里真实出现过的。
CODES: dict[str, str] = {
    "1048": "订单未找到（还没下单，正常）",
    "1018": "业务类型不合法（businessType 传错）",
    "1077": "ISV 不合法（isv 不是 CAMPUS）",
    "999": "服务开小差了（风控或参数缺）",
    "CAMPUS_AREA_INFO_ID_NULL": "缺 campusAreaInfoId",
    "FAIL_SYS_BIZPARAM_MISSED": "缺业务参数（报错会点名缺哪个）",
    "FAIL_SYS_UNAUTHORIZED_ENTRANCE": "接口未授权（resume 就是这种）",
    "FAIL_SYS_TOKEN_EMPTY": "令牌为空（首次握手，重试即可）",
}


def explain(err: MtopError | dict) -> str:
    """把错误翻成人话，便于 CLI 直接打印。"""
    code = err.code if isinstance(err, MtopError) else str(err.get("code", ""))
    return CODES.get(code, code)


__all__ = [
    "API_ACTIVITY", "API_COUPON_LIST", "API_DEVICE_INFO", "API_DEVICE_LIST",
    "API_FUND_DEVICE_INFO", "API_ORDER_DETAIL", "API_ORDER_STATUS",
    "API_PRE_START", "API_UNLOCK", "API_USER_ACTIVITY",
    "API_WATER_START", "API_WATER_STOP",
    "BUILDING_ID", "BUSINESS_TYPE", "CAMPUS_AREA_CODE", "CODES",
    "DEVICE_DRINKING", "ISV", "MAX_OUT_WATER_AMOUNT", "PAYEE_ID",
    "PAYMENT_CHANNEL", "RELATIONSHIP_ID",
    "ScannedDevice", "WaterApi", "explain", "parse_qr",
]
