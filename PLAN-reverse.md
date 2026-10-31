# PLAN-reverse — 逆向路线计划

> TMallKey 的逆向工作总规划。现状、已封死的路、待验证项、以及未来路线的触发条件与工程量。  
> 原则：**只为产品服务** —— 没有真实用户需求的逆向不立项。  
> 关联文档：DEVDOC.md（协议细节）、PLAN-drinking.md（饮水链路）、lspatch-module/README.md（模块）。

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
| 补丁版 CM 档案                 | 登录时 CM 建档校验 native 反作弊头（真实签名），补丁签名被拒 | 10-21 / 10-30 复证 |
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

- [ ] 阶段 4 · 一致性验证（unidbg 产物 vs 真机抓包；CM❌ 会话 + 自产 wua 建档）
- [ ] 阶段 5 · fkTM 集成（wua 生成器落地方式届时定）
- [ ] 阶段 6 · 维护策略（App/SDK 更新 → 重提 SO → 回归）

**已确认的旁证（10-31）**：`x-mini-wua` 由 mtop 的
`InnerProtocolParamBuilderImpl` → `getSecBodyDataEx(timestamp, appKey, authCode, ext, 8)` 产出；
`x-sgext` 来自 middletier 的 `getUnifiedSign`（两条独立链路，都要）。

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
