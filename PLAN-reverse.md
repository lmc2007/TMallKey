# PLAN-reverse — 逆向路线计划

> TMallKey 的逆向工作总规划。现状、已封死的路、待验证项、以及未来路线的触发条件与工程量。  
> 原则：**只为产品服务** —— 没有真实用户需求的逆向不立项。  
> 关联文档：DEVDOC.md（协议细节）、PLAN-drinking.md（饮水链路）、lspatch-module/README.md（模块）。

---

## ★ 2026-11-01 11:40 重大修订：CM 身份是 `.confong.cn` 的 cookie2，与补丁无关

**结论（已实验闭环）**：

1. CM 身份 = **`.confong.cn` 域的 `cookie2` 单条**。taobao 域 cookie2、SG 各头都不参与。
2. **补丁版登录照样会签发 confong cookie2**：11-01 11:31:59 补丁版重登后，
   `.confong.cn` 与 `.taobao.com` 的 cookie **同一秒创建**，且 `CookieProcessor` 把它复制到
   `.tmallcampus.com`。
3. 拿补丁版这条 cookie2 从 PC 直连 mtop（appKey 12574478）实测全通：
   `member.app.user.get`（CM✅ 昵称/手机号）、`guide.zmst.unlock.data.get`（encryptData 52 字符）、
   `activity.list.withdetail`、`member.user.login` 全 SUCCESS。
4. ⇒ **免 root 链路不需要 SecurityGuard**：x-sign / wua / x-sgext 对 CM 全部不必需。

**因此作废的旧结论**：

| 旧结论 | 为什么错 |
|---|---|
| CM 身份 = taobao cookie2 | 实际是 confong 域那条；taobao 那条只回 `SESSION_EXPIRED` |
| 补丁版会话永远 CM❌（登录时 native 建档校验签名） | dump 来自模块 socket，只有 taobao 域，**从来没带过 confong cookie2** |
| 必须 native mtop sync 才能激活 CM | 只要有 confong cookie2，纯 PC h5 请求就能读 CM 数据 |

**新的产品路径（免 root）**：
补丁版登录 → 读自己 WebView 的 `.confong.cn` cookie（`CookieManager.getCookie`，无需 root）
→ 交给 fkTM → fkTM 用这条 cookie 调 mtop → 接水 / MV 解锁。
有效期到 **11-24**，每次重新登录会重签发。

**已落地的代码修复（11-01 12:10）**：

| 位置 | 问题 | 修法 |
|---|---|---|
| `WaterSession.parseCookieDb`（app） | 多域拍平后 `.taobao.com` 的 cookie2 覆盖 confong 的 → 导入即丢 CM | 新增纯函数 `mergeCookieRows()`，confong 域 cookie2 最后写 |
| `Hook.dumpCookieMap`（模块） | 同上（HOSTS 里 confong 在前，必被覆盖） | 记下 confong 的 cookie2，最后统一写 |
| `MtopClient.absorb`（app） | mtop 响应带的 `cookie2;Domain=.taobao.com` 用几次就把 CM 会话换掉 | 已有 cookie2 时只接受 confong 域覆盖；空 jar 照收 |
| `Cookies.update_from_set_cookie`（py） | 同上 | 同上 |

验收：`fktm/water/cmcheck.py` 用补丁版 cookie 库跑通
（CM✅ 昵称/手机 + 水卡 + `zmst.unlock.data.get` 的 encryptData 52 字符），
连跑两轮稳定；`selftest.py` 74/74、`CookieMergeTest` 7/7；双端编译通过。
**未做**：设备安装与真机点「导入」（等操作员授权）。

---

## 一、现状盘点（2026-10-30）

### 已完全逆向 ✅

| 目标            | 产物                                                                        | 状态                 |
| ------------- | ------------------------------------------------------------------------- | ------------------ |
| VOC 门锁 BLE 协议 | 0D/13/0B 握手、AES-128/ECB、静态 activationKey                                  | 离线开锁，产品核心，长期稳定     |
| MV 锁（掌门神通）开锁链 | `guide.zmst.unlock.data.get {macId}` → `encryptData` → BLE AFF0/AFF1/AFF2 | **端到端已验证可用**（用户确认） |
| CM 会话机制       | cookie2 = CM 身份钥匙；激活 = native mtop sync（打开校园页面触发）；补丁版会话永不激活（签名）           | 已实验闭环              |
| MV 锁检测        | logcat `FastBle: mac: XX:XX…`（连接事件，设备名 MVL 前缀）                            | root 抓取双协议监听已实现    |
| 免 root 登录态同步  | LSPatch 模块 + 本地 Socket（127.0.0.1:42741）                                   | VOC 钥匙抓取可用         |

### 已封死（勿再踩）⛔

| 路线                        | 死因                                   | 日期               |
| ------------------------- | ------------------------------------ | ---------------- |
| ~~补丁版 CM 档案~~ | ~~登录时 CM 建档校验 native 反作弊头~~ → **11-01 推翻**：当时 dump 缺 `.confong.cn` cookie2，实际补丁版有 CM（见文件头修订） | 10-21 / 10-30 ❌作废 |
| `member.user.login` 补建档   | 返回 doHaveLogin:true 但 CM 层不变         | 10-21            |
| 修 cookie 补 CM（3PcFlag 等）  | 逐条二分证实只看 cookie2                     | 10-30            |
| 淘宝 H5 / 淘宝授权 / 短信 / 运营商一键 | appkey+签名+反作弊（wua）绑定                 | 10-20            |

### 边界（当前能力矩阵）

| 功能     | root                    | 免 root（补丁版） |
| ------ | ----------------------- | ----------- |
| VOC 门锁 | ✅ 抓一次永久离线               | ✅ 模块抓取      |
| MV 锁   | ✅ 在线开锁（root 抓取 + CM 会话） | ❌ 永久不可能     |
| 接水     | ✅ 同上                    | ❌ 同上        |

---

## 二、待验证 / 待收尾

| 项                  | 内容                    | 备注                |
| ------------------ | --------------------- | ----------------- |
| `encryptData` 重放测试 | 旧帧重放给锁能否开（判断锁端是否验新鲜度） | 需真锁在场，纯研究性质，不影响产品 |

> **关于天猫校园版本**：官方实际最新版已 7.0+，但 **5.0 仍完全可用** —— 服务端对旧版本没有  
> 强制升级门槛。因此不存在「新版本逼着重逆向」的压力，版本监控项取消；仅当用户实测发现  
> 5.0 某功能失效时再被动处理。



---

## 三、逆向路线计划

### P0 · MV 用户支持闭环 ✅（已完成，用户确认可用）

**目标**：MV 用户（root）从抓取到开锁全流程可用。

- [x] 双协议抓取（VOC 钥匙 / MV MAC 自动识别入库）
- [x] 在线开锁（联网取帧 + BLE 写入 + 预连接）
- [x] 进程被杀后会话恢复（WaterSession.ensure）
- [x] 真锁端到端验证（用户确认正常使用）
- [ ] 反馈驱动的修复循环（长态，有反馈再修）

**触发下一项的条件**：MV 用户群扩大（>1 人）或用户反馈集中。

### P1 · native 登录逆向（unidbg 路线）—— **已立项，进行中（2026-10-31）**

**目标**：自产合法 `x-mini-wua`，使 CM 建档对任意会话生效 → 免 root 全功能。

**技术路线**：unidbg 模拟执行官方 SO（不逆算法，直接调）。
工程与复现脚本：`reverse/unidbg-harness/`（源码入库；SO 与资产在 `tools/unidbg-tmall/`）。
README 里有完整事实表、命令码、JNI 桩坑与 bootstrap 步骤。

- [x] **阶段 1 · 资产提取**（2026-10-30）
      `libsgmainso-6.5.97` / `libsgmiddletierso-6.5.90` / `libsgsecuritybodyso-6.5.104` /
      `libsgcore` + `yw_1222.jpg`。
      **关键补充（10-31）**：`libsgmain.so`、`libsgmiddletier.so`、`libsgsecuritybody.so`
      **不是 ELF，是 zip 包（内含 classes.dex）** —— SecurityGuard 的 Java 层就在这里，
      命令码 10101/10401/20102 全部从中反编译得到，不是猜的。

- [x] **阶段 2 · unidbg harness**（2026-10-31）
      `libsgmainso` 加载成功（arm64，SONAME=`libmain.so`）、`JNI_OnLoad` 通过、
      `doCommandNative` 可调、`initialize(10101)` **返回 0（初始化成功）**。
      三个插件 so（core/middletier/body）也能加载并完成 JNI_OnLoad。
      踩过的坑：ProxyClassFactory 的反射路径会吞掉 JNI 回调（改纯 DvmClass）；
      unidbg 不接受裸 `Object[]`（要 `ArrayObject`）；`SGPluginExtras.slot` 必须真存；
      `fstatat(dirfd, NULL)` 让 unidbg NPE（已打补丁，见 `patches/`）。

- [x] **阶段 3 · wua 产出** —— ✅ 打通（2026-10-31 16:50），待服务端验证
      补充（19:05）：x-mini-wua 实测**恒为 69 字符**（四种参数组合一致），
      这是该 SG 版本的正常格式，不是"信息缺失"。appKey 已纠正为真机的 **34270422**。
      `doCommand(20102, …)` / `20101` 均返回 `201029904`。

      **9904 的语义已经用 IDA 查实（不是猜）**：`libsgmainso` 0x38F10–0x38F68 是一个
      **线性查表循环** `for(i) if(table[i]->cmd == cmd) goto handler;`，没命中就走
      `MOV W9,#0x26B0; ADD W8,W8,W9`（cmd×10000 + 9904）。即 **2xxxx 不在分发表里**，
      不是参数错、不是初始化没跑完。

      **三条路已经排掉两条**：
      - ① 直接调 slot：❌ 实测否定（iolog 证明 native 初始化全程**没碰** `lib/arm64`，
        不 dlopen 任何插件 so）。表是空的，绕过 Java 调 slot 也接不上。
      - ③ 改走 middletier `getUnifiedSign`：❌ 同一个病根（15xxx 也要 Java 注册）。
      - ② **宿主 JVM 跑 SG 的 Java 编排层**：✅ 选定。工程在 `reverse/sg-java/`。

      **为什么只有②能成**：插表动作在 `SGPluginManager → onPluginLoaded` 这条 Java 链上，
      而 unidbg 的 DVM 不执行 dex 字节码（只提供签名表 + 反射桥）。Java 不跑，表永远空。

      当前进度（16:30）：**Java 全链路已通，但插表仍未补上**。
      - `reverse/sg-java`：三个插件 dex 的 Java 层编译通过（154 class），
        把 `System.loadLibrary` 与 `JNICLibrary.doCommandNative` 抽成钩子。
      - `reverse/android-impl`：android.* 可用实现（Context/SharedPreferences/pm/os/telephony…）。
      - `SGRunner`：main 插件 `onPluginLoaded` 跑通，`10101` 经 Java 调用返回 **0**；
        body 插件加载时 `loadLibrary` 钩子真的加载了 `libsecuritybody.so` 并跑 JNI_OnLoad，
        `SGPluginExtras.slot` 被写入 `0x13284f0b`。
      - 但 `20101/20102` 仍是 9904 —— 主模块处理 2xxxx 时**不读** `SGPluginExtras.slot`
        （verbose 里只有 Set 没有 Get）。说明分发表不是插件 JNI_OnLoad 填的，
        而是框架拿到 `onPluginLoaded` 返回的 `IRouterComponent` 后交给 native 登记。
      - 卡点：decompiled dex 里**找不到 `onPluginLoaded` 的任何调用点**（classes8 框架类
        有 27 个 jadx 反编译失败，`d.java` 1295 行多 JADX WARN），这段大概率丢了。

      **✅ 已解决（16:50）**：漏了命令 **10102 = 插件登记**。
      `jadx --show-bad-code` 补全 `framework/d.java` 后拿到真相（第 820-857 行）：

      ```java
      } else {
          SGPluginExtras.slot = 0L;                     // 加载后续插件前先清零
          plugin.onPluginLoaded(context, this.f, info, name, flags);  // 插件内 loadLibrary
      }
      ...
      r.doCommand(10102, {插件名, str11, so 绝对路径});   // ← 登记，插表就在这里
      ```

      body so 的 JNI_OnLoad 只负责把分发函数指针写进 `SGPluginExtras.slot`，
      **真正把 2xxxx 插进主模块分发表的是随后的 10102**。我们跳过了它，所以表永远空。

      补上后（SGRunner）：

      ```
      [RUN] native doCommand(10102) -> 0   slot=13284f0b
      [RUN] native doCommand(20101) -> true
      [RUN] native doCommand(20102) -> aPQRPTQAe+Gbgjssq2y7bK9ouhkbSrSXi06nY7sf7vQ//...
      ```

      `flag=8` 得到 51 字节原始数据（base64 69 字符）；`flag=4` / `flag=1` 报 1412（待查）。

- [x] 阶段 4 · 一致性验证（2026-10-31 23:50）—— **达成**
      x-sign 与真机逐字节一致（rt=7: `ab2e7501…`, rt=3: `d929…`, rt=2: `0ccb…`）；
      自产 x-sign 打 `mtop.common.gettimestamp` 返回 **`SUCCESS::接口调用成功`**
      （PC 直连，不经过真机、不需要 root）。
      产物：`SignCli`（22 段 INPUT → 10401 → x-sign / x-mini-wua）+ `reverse/mtop_app_probe.py`。
      **前提**：必须 `-Dsg.apkmap=true` —— 608 的根因就是 SG 签名时读 `/data/app/<pkg>/base.apk`
      没映射上（`[IO-REQ] …base.apk` 出现在抛错前一行）。
      遗留：`mtop.taobao.mloginService.smsSend` 仍 `FAIL_SYS_PROTOPARAM_MISSED`
      （业务参数/风险参数/x-sign 位置四种组合全试过），缺的是一个未识别协议级参数，
      下一步靠真机抓一次明文请求头定性。
- [x] 阶段 4.5 · CM 层凭据定性（2026-11-01 11:50）—— **达成，且改写了一条公理**
      **CM 身份 = `.confong.cn` 域的 `cookie2` 单条**（不是 taobao 域 cookie2，不是 SG 头）。
      消融实验（`member.app.user.get`，h5 网关）：

      | 凭据 | 结果 |
      |---|---|
      | taobao 域全量 37 条 | `CM_FAIL_BIZ_USER_NOT_LOG_IN` |
      | taobao + confong | **SUCCESS（campusNickName=稀有的活力企鹅）** |
      | 去掉 confong 的 cookie2 | `FAIL_SYS_SESSION_EXPIRED` |
      | **只发 confong 的 cookie2 一条** | **SUCCESS** |
      | 只发 taobao 的 cookie2 | `FAIL_SYS_SESSION_EXPIRED` |

      只带这一条就能读到 `share.applet.activity.list.withdetail` 的真实校园数据；
      `fund.use.device.info.get` 等变成 `FAIL_SYS_BIZPARAM_MISSED`（说明登录已过，只缺业务参数）。
      产物：`reverse/cm_verify.py`。
      **冲击**：旧结论"补丁版会话永远 CM❌"证据不成立 —— 当时的 dump 只有 taobao 域，
      **从来没带过 confong cookie2**。待重测。
      未定论：confong cookie2 由谁签发（原生 cronet 写 CookieManager 后 WebView 读走；
      H5 里没有任何 SSO/API 路径，数据全走 WindVane→native mtop）。
- [x] 阶段 4.6 · confong cookie2 的签发方（2026-11-01 11:55）—— **查清机制，闭环缺一次登录**
      - 校园 H5 的 mtop 网关是 **`acs-m.confong.cn`**（appKey 12574478 + h5 md5 sign），
        纯浏览器打开时请求**不带任何淘宝身份**，只拿匿名 cookie2；H5 只"检查"登录
        （`member.user.login` 读 `doHaveLogin`），不发起登录。
      - App 里没有生成 confong cookie2 的代码，只有搬运：
        `com.tmall.campus.bizwebview.cookie.CookieProcessor` 在 `NOTIFY_LOGIN_SUCCESS` 时
        把 `.confong.cn` 的 `cookie2` 复制到 `.tmallcampus.com`，登出时删掉。
      - 设备 Cookie 库：confong 的 6 条 cookie 与 taobao 的 cookie2/unb/_tb_token_
        **同一秒创建（11-01 10:20:48）** ⇒ 由一次登录/SSO 同时下发，不是 SG 计算的；
        有效期到 **11-24**。
      - 卡点：现有两套淘宝 WebView 会话都已被服务端判失效（h5 直接回 `unb=;Max-Age=0`），
        PC 上无法复现 SSO。**判定实验 = 重新登录一次后 dump WebView Cookies，
        看 confong cookie2 是否刷新。**
      产物：`reverse/web_sso_probe.py`（无头 Chrome + CDP 抓请求/Set-Cookie）。
- [x] 阶段 4.7 · 门锁自动导入（A 路线，2026-11-01 13:00）—— **VOC 截钥匙 / MV 走在线**
      - `guide.door.lock.user.get` 从 fkTM 侧不可用：h5 → `FAIL_BIZ_CE003::参数异常`，
        `/gw` + 自产 x-sign → `FAIL_SYS_PROTOPARAM_MISSED`。所以 VOC 的 `activationKey`
        只能从**门锁页 H5 → `VocLockHelper.unlock(JSBParams)`** 里截，
        「打开一次天猫校园」必须包含**打开一次门锁页**，之后 fkTM 自己 pull，不再需要它。
      - 模块改为推**整包** `{cookies, doorlocks, mvlocks}`，抓到锁 1.2s 内补推一次；
        fkTM 侧 `ModuleEnvelope` 解析 + `LockImport`（VOC 优先）入库，
        主界面 ON_RESUME 即 pull（`UnlockViewModel.syncFromModule`）。
      - **MV 锁不需要钥匙**：`guide.zmst.unlock.data.get { macId }` 用 confong cookie2
        就能拿 `encryptData`（52 字符），开锁时现取现写 AFF1。
        实测 **macId 必须是冒号格式**，去掉冒号回
        `FAIL_BIZ_ZMST_DOOR_GET_UNLOCK_DATA_ERROR`（业务错，换格式没意义）。
      - **实测修正（13:25）**：只「打开门锁页」拿不到钥匙 —— 页面只发 `door.lock.user.get`，
        其响应里没有 `secretKey`（`MtopWVPlugin.wvCallback` 探针里完全没有 lock 相关 data），
        `activationKey` 只在用户**点一次开锁**时作为 `VocLockHelper.unlock(JSBParams)` 入参出现。
        所以免 root 的首次动作是「开门锁页 + 点一次开锁」，之后完全脱离天猫校园。
      - 回归：单测 89/89；设备 `providerLocks | voc=1 mv=0` + `syncPull doorlocks=1`，
        UI 列表出现「门锁 02:97 / 钥匙 160d…99ca」；PC `zmst.unlock.data.get` → SUCCESS。
- [x] 阶段 4.8 · smsSend 协议层破译（2026-11-01 14:50）—— **协议通，卡风控**
      产物：`reverse/sms_app_probe.py`（可复跑）。全部结论都是实测，不是推断。

      **① `/gw` 会逐个点名缺哪个协议头** —— 这是一条免费的枚举通道：

      ```
      无头            → FAIL_SYS_PROTOVER_MISSED::缺少协议版本m-pv
      +m-pv           → FAIL_SYS_PROTOPARAM_MISSED::缺少协议参数m-appkey
      +m-appkey       → ...m-sign
      +m-sign         → ...m-t
      +m-t(毫秒)      → FAIL_SYS_REQUEST_EXPIRED::请求失效
      +m-t(秒)        → FAIL_SYS_ILEGEL_SIGN::非法请求签名
      ```

      即老协议骨架 = `m-pv / m-appkey / m-sign / m-t`，且 **m-t 必须是秒**
      （毫秒会被当成"五万年后"直接判过期）。

      **② `m-*` 这条路走不通**：反查 APK 15 个 dex，`m-sign` 唯一命中是
      `x-am-sign` 的子串 —— 天猫校园自己**从不发** `m-*`。这是服务端对
      "认不出来的客户端"的兜底分支，`m-sign` 的算法服务端独有，无解。
      别再往这里投时间。

      **③ 真机形态的协议头是对的**（同一套头打两个 API，对照实验）：

      | API | 结果 |
      |---|---|
      | `mtop.common.gettimestamp` | **SUCCESS::接口调用成功** |
      | `mtop.taobao.mloginService.smsSend` | `PROTOPARAM_MISSED::缺少协议参数`（不点名） |

      头 = `x-sign` + `x-appkey` + `x-devid` + `x-ttid` + `x-utdid` + `x-umt`。
      既然 gettimestamp 过，说明**签名和协议头完全正确**，缺的是 smsSend 专属的东西。

      **④ 缺的就是 `x-sgext`**（决定性实验）：

      ```
      x-sign + 上面 5 个 x-*          → PROTOPARAM_MISSED
      再加 x-sgext: "z"*180（假值）    → HTTP 419 FAIL_SYS_USER_VALIDATE::哎哟喂,被挤爆啦
      ```

      假 sgext 都能让**协议层立刻放行**，说明服务端只检查"有没有"；
      内容真伪留给风控层。而风控层这一挡就是硬挡（`data` 为空，连滑块地址都不给）。

      **结论**：短信登录的完整前置 = `x-sign` + `x-mini-wua`（已有）+ **真 `x-sgext`**。
      三者都来自 SecurityGuard，其中 x-sgext 在 middletier（`getUnifiedSign`），
      而 middletier 的 `70101` 初始化仍卡 `2405`（UT 桥已返回 1，桩已补，未过）。
- [x] 阶段 4.9 · RGV587 里其实藏着滑块地址（2026-11-01 14:25）—— **h5 链路起死回生**
      上一条把 x-sgext 判成必需，是**只在 App 网关成立**。h5 网关根本不要 sgext。

      实测同一发码请求（h5 网关，`data` 放三个 JSON，不带 token、不带 umid）：

      ```json
      {"ret":["FAIL_SYS_USER_VALIDATE","RGV587_ERROR::SM::哎哟喂,被挤爆啦,请稍后重试"],
       "data":{"url":"https://acs.m.taobao.com:443//h5/mtop.taobao.mloginService.smsSend/1.0/_____tmd_____/punish?x5secdata=…&x5step=2&action=captcha&pureCaptcha=true"}}
      ```

      **`RGV587_ERROR` 不等于失败** —— 它是"要你做滑块"，而且 `data.url` 就是滑块页
      （`pureCaptcha=true`，打开即纯滑块，滑完种 `x5sec`）。

      **真正的 bug**：`AliLogin.sendSms` 只在 `code == FAIL_SYS_USER_VALIDATE` 时去取 url，
      而 `code` 取的是 `ret[0]`。服务端 `ret` 有**两种形态**：
      `[FAIL_SYS_USER_VALIDATE, RGV587_ERROR::…]` 和 `[RGV587_ERROR::…]`。
      遇到第二种就直接当"限流失败"返回，滑块页**永远不会被打开** ——
      这就是"h5 逻辑走不通"的全部原因，不是风控真的封路。

      修法（已落地）：`sendSms` 里**先取 punish url，再判错误码**；
      `punishUrl()` 同时兼容 `data.url` / `data.punishUrl` / `data` 被序列化成字符串 /
      正则兜底。真·限流（响应里确实没有 url）才提示冷却。

      产品结论：**免 root 短信登录不需要 SecurityGuard** —— h5 网关 + 滑块 + `x5sec`
      就够，App 网关那条 `x-sgext` 死路可以彻底放弃。

      > ⚠ **2026-11-01 16:00 这条结论被推翻**：操作员实测天猫校园发码**从不弹滑块**——
      > 官方客户端是被服务端当"自己人"直接放行的。h5 那条即使滑过也是低信用会话，
      > 不解决问题。真正的差距仍在「客户端身份」，见阶段 4.10。
- [x] 阶段 4.10 · fkTM 进程内跑通 SecurityGuard（2026-11-01 15:00）—— **基础设施达成**
      产物：`fktm-android/.../water/SgEngine.kt` + `ui/SgProbeActivity.kt`。
      做法：给 SG 一个**包装过的 Context**，让它在 fkTM 进程里以为自己是天猫校园。

      | 关卡 | 现象 | 解法 |
      |---|---|---|
      | Android 14+ 禁加载可写 dex | `SecurityException: Writable dex file` | 用 `InMemoryDexClassLoader` 把 APK 里 15 个 `classesN.dex` 全读进内存 |
      | Android 10+ 禁从可写目录 dlopen | `SecException: plugin main not existed` | SG 的 so 打进 `jniLibs/arm64-v8a`，`useLegacyPackaging=true` + `keepDebugSymbols` |
      | SG 按 `sourceDir` 校验签名 | — | `ContextWrapper` 伪装包名 `com.tmall.campus.and`、`sourceDir` 指向 tmall.apk 副本 |
      | SG 绕回 `getApplicationContext()` 拿包名 | `2403` | override 返回包装自身 |
      | 缺密钥资源 | — | `yw_1222.jpg` 放进 assets |

      实测日志（`adb shell am start -n com.fktm.app/.ui.SgProbeActivity`）：
      ```
      getInstance ok in 429ms
      unified component = $Proxy11          ← middletier 插件已加载成功
      init(<空>)     失败 code=2405
      init(0)        失败 code=2403
      init(24字符)   失败 code=2401
      ```
      **卡点**：middletier 的 `70101 init(authCode)`。空→2405、1~20 字符→2403、≥22→2401。
      PC 的 unidbg 与真机 fkTM **行为完全一致**，证明不是环境问题，是 authCode 内容校验。

- [x] 阶段 4.11 · 抓包（2026-11-01 16:00）—— **五条路全失败，代价大**

      | 方案 | 结果 |
      |---|---|
      | frida hook `SSL_write`（spawn + module observer） | 抓到 libssl 明文（日志 SDK、匿名 getTimestamp），**抓不到 mtop** |
      | mitmproxy 系统代理 + `adb reverse` | 只有 HTTP 进代理，HTTPS 不进 |
      | mitmproxy + iptables DNAT + 系统证书 bind mount | **把手机搞断网**，且流量没到代理 |
      | Reqable（VPN 模式，操作员自装） | 能解密 pangolin/hmma 的 HTTPS，**抓不到 mtop** |
      | tcpdump（root） | 看到 App 连 `203.119.238.48:443` 等阿里 CDN（31 包），内容加密 |

      **天猫校园的 mtop 走 tnet/cronet，绕过 VPN、绕过系统代理**（大概率 `bindSocket` 绑网卡），
      只有内核层能看见，但看见了也是密文。

      **教训（后来者必读）**：
      - **不要**在操作员手机上反复改 iptables / 装系统证书 / 改 `http_proxy`。
        已经两次导致设备断网，代价由操作员承担，操作员明确反对。
      - 这条路本机已支出 >2 小时，**收益为零**。要抓 mtop，只能从 App 进程内部拿
        （模块 / hook），不要在外围折腾网络栈。

      **唯一收获（HAR 情报）**：
      ```
      POST http://amdc.m.taobao.com/amdc/mobileDispatch?appVersion=5.0.0&appkey=34270422&v=6.3
      appName   = TmallCampusAndroid
      channel   = 36324434270422
      User-Agent= Dalvik/2.1.0 (Linux; U; Android 16; PKR110 Build/AP3A.240617.008)
      signType  = sec   sign = 40 位 hex（HMAC-SHA1）
      ```
      ⇒ appKey `34270422` **确认有效**（`4272` 这种会回 UNKNOWN_APP）。
      但 **`34270422` 与 `TmallCampusAndroid` 在天猫校园的 15 个 dex、90MB 原生库、资源里
      全都搜不到** —— 运行时才拿到（服务端配置或加密配置），**静态提取 appSecret 这条路不通**。
- [ ] 阶段 5 · fkTM 集成（wua 生成器落地方式届时定）
- [ ] 阶段 6 · 维护策略（App/SDK 更新 → 重提 SO → 回归）

**已确认的旁证（10-31）**：`x-mini-wua` 由 mtop 的
`InnerProtocolParamBuilderImpl` → `getSecBodyDataEx(timestamp, appKey, authCode, ext, 8)` 产出；
`x-sgext` 来自 middletier 的 `getUnifiedSign`（两条独立链路，都要）。

### P1 补充 · 20:00 收口（10-31）

**x-sign(10401) 的 608 已经定位到"谁抛的"，但没定位到"为什么"**：

| 事实 | 证据 |
|---|---|
| 608 是 10401 处理链自己返回的裸状态码 | `sub_11F630`(0x11F630) 唯一调用点 0x3B4C0 在 `doCommandNative` 尾部错误收口；`sub_39338` 查不到错误码条目 → `SecException(608,"")` |
| 参数已被完整读取 | verbose：5 个实参全部 `GetObjectArrayElement` 读完 + `booleanValue()` 后才抛 |
| 不是 JDK 反射 | 全量 `--add-opens` 后 `InaccessibleObjectException` 消失，仍 608 |
| 不是异步初始化没跑完 | `-Dsg.sleep=8000` 仍 608 |
| 不是 requestType | 1..10 全 608 |

**工具能力纠正**：`Backend#hook_add_new(CodeHook,…)` 两个后端都可用。
之前 hook 不触发是因为 **libmain 被加载两份**（0x12000000 / 0x12600000，执行的是后者）。
新增 `SGTrace.java`（`-Dsg.trace=` / `-Dsg.trace.sp=` / `-Dsg.trace.deref=`）与
`-Dsg.verbose` 下 JNI 返回地址定位法 —— 这是后续查 native 问题的标准手段。

**下一步主攻假设（当晚已被推翻）**：608 可能是 **AVMP(module 6) 未就绪** 的快速失败。
实际根因是 **`/data/app/<pkg>/base.apk` 没映射**（见下节），加上 `-Dsg.apkmap=true` 后
10401 直接出签名，且与真机逐字节一致。

| 维度         | 评估                                    |
| ---------- | ------------------------------------- |
| 工程量        | 阶段 1~2 = 1 天；阶段 3 走②，已从 259 个编译错误收到 ~90 |
| 技术风险       | 中（② 的未知量集中在 Android 框架桩与签名校验）           |
| 维护成本       | 高（每次更新重适配）                            |
| 收益         | 免 root 全功能；摆脱补丁版依赖                    |


### P2 · 被动监控（issue 驱动）

- 新锁型 / 新协议 → 先定性（tmxy_check.bat / 双协议抓取是现成工具）
- 补丁包自动重打已由 CI 覆盖（release.yml Patch 步骤）
- ~~天猫校园新版本监控~~ —— 已取消：官方最新 7.0+，5.0 仍完全可用，无升级压力；  
  仅当用户实测发现 5.0 某功能失效时被动处理

---

## 四、原则

1. **用户驱动**：没有真实用户需求不立项逆向（当前唯一受益者计数 = 立项门槛）。
2. **只读不写**：逆向产物只做读取/本地复刻，不改天猫校园任何数据。
3. **凭证不上云**：activationKey / cookie 只存本机 Keystore 加密区。
4. **先定性再动手**：新问题先用现成工具（双协议抓取 / tmxy_check.bat / PC 复现链）定性，避免盲改。
5. **每条结论必须实验闭环**：本文件里所有「已封死」都有实验记录，不做无据推断。
