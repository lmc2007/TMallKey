# 校园饮水服务 · 实施方案 v4

> v1 探查 → v2 定路线（C）→ v3 参数实证 → **v4 解锁链路全线打通**。
> 2026-10-20 晚：手机端 `water.start` 实调成功，订单 0 元自动成单。
> 状态：`fktm/water/` 四层已落地，自检 **60/60**。

---

## 0. 一页结论

| 项 | 结论 |
|---|---|
| 登录协议 | **不用复现**。WebView 的 cookie 是**明文**存在 SQLite 里，root 直接读 |
| 二维码 | `https://share.confong.cn/xh?id=tmxy869451084947543` |
| **解锁入口** | **`device.water.start` 一个接口搞定**，不需要 `bizOrderId`，也不需要 `unlock` |
| 免费机制 | `fund.device.pre.start.info.get` 返回 `{}` = 无预授权；解锁后不按键 → 超时 **0 元自动结单** |
| 出水方式 | `water.start` 只是**远程开锁**，出水靠机器上的**物理按键**（页面会提示「请在设备上操作出水/暂停」） |
| 已跑通 | 签名 / 只读接口 / **解锁实调** / 0 元成单，全链路真机验证 |
| 不可用 | `device.water.resume`（`UNAUTHORIZED_ENTRANCE`） |
| 抓包工具 | `fktm/water/cdp_capture.py`（hook `lib.mtop.request`，含响应体） |

### ⚠ 三个命名陷阱（本方案最容易写错的地方）

同一个值在不同接口叫不同名字，同一个名字在不同接口指向不同值：

| 名字 | 在哪儿 | 值 |
|---|---|---|
| 二维码 `deviceCode` | 二维码 | `tmxy869451084947543` |
| 真 `deviceId` | `fund.*` / `device.user.activity.get` | `869451084947543` |
| `resNo` | 设备编号 / 水机资源号 | `630102040` |
| **`water.start` 的 `deviceId`** | 解锁接口 | **`630102040`（是 resNo！）** |
| **`activity.list` 的 `deviceCode`** | 活动查询 | **`630102040`（也是 resNo！）** |
| `timelimit.discount` 的 `imei` | 限时折扣 | `869451084947543`（字段名叫 imei，塞的是 deviceId） |

拿二维码里的 `869451084947543` 去调 `water.start` 会失败 —— 它要的是 resNo。

---

## 1. 登录协议：不用复现，也复现不了

你问「直接复现他的登录协议可以吗」。拆成两件事：

### 1.1 从设备取登录态 —— 可以，而且很简单

我原以为 WebView 的 cookie 是 Keystore 加密的（Chrome 浏览器那样），**实测是错的**：

```
cookies 表：总数 20，有 encrypted_value 的 = 0，有明文 value 的 = 20
```

**WebView 的 CookieManager 用明文 SQLite**（只有 Chrome 浏览器才走 os_crypt）。
所以 root 下 `cp` 出来就能直接用，不需要 hook、不需要解密。

内容（只看名字，值没打印）：`.confong.cn` 域下有 `cookie2` / `_tb_token_` /
`_nk_` / `munb` / `tfstk` / `isg` / `csg`，**有效期到 2026-11-13**。

### 1.2 自己实现淘宝登录 —— 不行，别做

| 障碍 | 说明 |
|---|---|
| 设备指纹 | 登录态绑定 `umidToken` / `x5sec`，换设备就重验 |
| 行为验证 | 新设备登录必触发滑块/短信，脚本过不了 |
| App 签名 | mtop 之外还有 `x-sign` 一族，由设备指纹 SDK 生成 |
| 后果 | 第三方客户端登录是明确的封号理由 |

**结论：读 cookie，别登。**

代价说清楚：cookie 会过期（这次是 24 天），过期后要重新同步；卸载天猫校园就失效。

---

## 2. 二维码与参数（全部来自真机抓包）

### 2.1 扫码

实测扫出来的原文：

```
https://share.confong.cn/xh?id=tmxy869451084947543
```

⚠ `/xh` **不是**业务页，是静态引导页（打开只显示「请使用天猫校园APP扫描二维码」，
里面只有 `SHARE_ISV_MAP={XN:1,HB:2,MD:3}` 用来选提示图）。真正的解析在 App 内。

解析规则就一条：

```
deviceCode = tmxy869451084947543     （id 参数）
deviceId   =  869451084947543        （去掉 tmxy 前缀）
```

### 2.2 设备上下文（这台机器的值）

| 字段 | 值 | 来源 |
|---|---|---|
| `deviceCode`（二维码） | `tmxy869451084947543` | 二维码 |
| `deviceId`（真） | `869451084947543` | 去前缀 |
| `resNo` | `630102040` | 设备列表 / 设备信息 |
| `campusAreaCode` | `500960` | 页面上下文 |
| `campusAreaInfoId` / `washServiceAreaId` | `804568` | 页面上下文 |
| `relationShipId` | `10915` | 页面上下文 |
| `payeeId` | `14735` | 页面上下文 |
| `buildingId` | `3336` | 设备列表（仰山公寓 A）**v4 新增** |
| `relationshipId`（设备列表用） | `1` | 设备列表 **v4 新增**，和 10915 不是一个东西 |
| `washDeviceType` | `DRINKING_MACHINE` | — |
| `maxOutWaterAmount` | `200` | ml |
| `front-app-ver` | `1.1.0` | H5 版本头 |

⚠ `resNo` / `relationShipId` / `payeeId` / `campusAreaCode` **不在二维码里**，
是打开页面时由接口注入的。换校区/换机要重新取。

---

## 3. 实测调用链（2026-10-20，真机全流程）

`deviceConsumeSourceStr=FAST_START` 路径，共 19 次 mtop 调用，关键几条：

### 3.1 进设备页（H5 一次性打 8 个）

| 接口 | 入参 | 备注 |
|---|---|---|
| `general.device.info` | `{isv:CAMPUS, businessType:WASH_AND_CARE, resNo:630102040, paymentChannel:TMXY_APP}` | 扁平 |
| `activity.list.withdetail` | `{deviceCode:630102040, campusAreaCode:500960, relationShipId:10915, washDeviceType:DRINKING_MACHINE}` | **扁平**，`deviceCode` 是 resNo |
| `fund.use.device.info.get` | `{campusAreaCode, businessTypeEnum, payeeId:14735, relationShipId:10915, deviceId:869451084947543, paymentChannel}` | 扁平，**deviceId 是真 ID** |
| `fund.device.pre.start.info.get` | `{campusAreaCode, deviceId, paymentChannel}` | 扁平，返回 `{}` = 免费 |
| `water.coupon.list.get` | `{requestType, requestJson:{businessType, orderTypeCode:SERVICE_ORDER, resNo, deviceId, campusAreaCode, paymentChannel, deviceType, isv, maxOutWaterAmount:200}}` | **包装式** |
| `timelimit.discount.activity.query` | `{imei:869451084947543}` | 扁平，字段名是 imei |
| `device.user.activity.get` | `{deviceId:869451084947543}` | 扁平 |
| `customer.pop.list` | `{requestType, requestJson:{campusAreaInfoId:804568, campusAreaCode:500960}}` | 包装式 |

### 3.2 ★ 解锁（核心，已验证）

```json
mtop.tmall.campus.share.applet.device.water.start
{
  "deviceId": "630102040",
  "paymentChannel": "TMXY_APP",
  "deviceConsumeSourceStr": "FAST_START"
}
```

- **扁平参数**，没有 `requestType` / `requestJson`
- `deviceId` = **resNo**（630102040），不是 869451084947543
- **没有 `bizOrderId`** —— 订单由服务端在本次调用中创建
- 响应下发：`shareServiceInfoId=353064725`、`isvOrderId=360584003`

### 3.3 解锁后

页面变成「请在设备上操作出水/暂停」，**不出水**，等人按机器上的按钮。
不按则超时，跳到「¥0 支付成功」——**0 元自动结单，不扣费**。

此时再调 `device.user.activity.get` 会带上订单号：
`{deviceId:869451084947543, serviceId:353064725, isvOrderId:360584003}`

### 3.4 入参风格的真实分布（v3 结论要修正）

v3 说「大多数接口用包装式」，**实测反了**：

```
包装式（少数）：device.list、water.coupon.list.get、customer.pop.list
扁平（多数）：  general.device.info、activity.list.withdetail、
                fund.use.device.info.get、fund.device.pre.start.info.get、
                device.water.start、device.user.activity.get、
                timelimit.discount.activity.query
```

套错就是 `FAIL_SYS_BIZPARAM_MISSED`（报「缺业务参数」，实际是被包装吃掉了）。

---

## 4. 抓包工具（`fktm/water/cdp_capture.py`）

mtop 走 WindVane 原生通道，Network 域抓不到包。唯一可行路径是注入 hook 包住
`lib.mtop.request`。工具已固化，含**请求 + 响应**。

```bash
# 手机 USB 或无线 adb 均可，脚本自动挑设备（优先无线）
python -m fktm.water.cdp_capture probe        # 列 targets
python -m fktm.water.cdp_capture arm          # 注入（当前文档 + 跨页面持久）
python -m fktm.water.cdp_capture watch        # 实时打印，落盘 fktm/water/captures/
python -m fktm.water.cdp_capture dump         # 从手机 localStorage 取回（断线兜底）
```

### 四个非显然的坑

1. **devtools 只认 `Host: localhost`** —— 带端口的 `127.0.0.1:9222` 会建立 TCP 连接
   但**一个字节都不回**，看起来像服务挂了。必须自己拼 socket 请求。
2. **`/json` 不回 `Content-Length` 也不主动关连接** —— urllib 会一直挂着，得读到底再解析。
3. **`Page.addScriptToEvaluateOnNewDocument` 只对后续导航生效**，当前文档必须另用
   `Runtime.evaluate` 求值一次，否则永远等不到 `hooked`。
4. **一个 devtools socket 只服务一个客户端**。上一条连接没干净关闭时，后续连接
   全部卡住 —— 只能 `am force-stop` 重启 App 恢复。

### 断线兜底

hook 把每条记录同步写进 `localStorage.__fktm_cap`（上限 600 条）。
所以 PC 掉线、人走远了也不丢数据，回来 `dump` 一条命令取回。
这是「去现场抓包又不想拖 USB 线」时的保险。

---

## 5. 还差什么

| 待验证 | 说明 |
|---|---|
| **扫码路径的 `deviceConsumeSourceStr`** | 本轮走的是 `FAST_START`（设备列表「快速启动」）。扫码入口可能是别的值（如 `SCAN_CODE`），需抓一次 |
| **`water.stop` 的真实入参** | 本轮解锁后没按物理键，超时自动结单，没走到 stop |
| **`resNo` 的获取方式** | 扫码进来的路径怎么从 `tmxy...` 拿到 `630102040`，还没抓到（快速启动是设备列表直接给的） |
| **真实出水时的暂停/继续** | `resume` 不可用，只能 stop → start |

---

## 6. 代码（`fktm/water/`）

```
mtop.py         签名 / token 两次握手 / Cookie 吸收 / 错误分类    60/60 自检
api.py          parse_qr + WaterApi（只读 / 高危分开）
cdp_capture.py  真机取证（hook lib.mtop.request，含响应）
selftest.py     离线自检，不发网络
captures/       抓包产物（gitignore）
```

`api.py` 的设计约束：

- `start` / `stop` / `unlock` 必须显式 `confirm=True`，否则抛 `PermissionError`。
  这条是真的会解锁设备，不能靠「记得别调」。
- `explain()` 把 `1048` / `1077` / `1018` / `CAMPUS_AREA_INFO_ID_NULL` 翻成人话。
- `parse_qr()` 兼容三种输入：完整 URL / 裸 `tmxy...` / 纯数字。

回归：门锁 `cli.py selftest` 仍 **54/54**，两边完全独立。

---

## 7. 边界

| 红线 | 说明 |
|---|---|
| 只操作自己扫到的设备 | 不遍历设备列表，不猜 deviceId |
| 订单真实走 | 免费是学校给的，不是我们改的。**绝不篡改造价字段** |
| cookie 等同密码 | 只在本机 Keystore/内存，不落日志、不上云。这次取证用的副本已删 |
| 限流 | 高频调用会封号。调试时我控制在十几次以内 |
| 不碰付费 | 接水免费，洗澡要钱 —— 淋浴那条线**不做** |
