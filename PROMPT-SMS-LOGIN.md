# 短信登录交接提示词（复制全文给新会话）

---

你在 TMallKey 项目。工作区：`C:/Users/64702/WorkBuddy/Worktrees/TMSCHOOL/main-3318e7fe`
（主仓库在 `C:/Users/64702/Desktop/SRC/TMSCHOOL`，`tools/unidbg-tmall` 在 worktree 里是 junction）。

**先读这三份，再动手，不要跳**：
1. `PLAN-reverse.md` — 重点看阶段 4.7 / 4.8 / 4.9 / 4.10 / 4.11（我们今天的全部进展与死路）
2. `.workbuddy/memory/2026-11-01.md` — 今天的原始记录
3. `fktm-android/app/src/main/java/com/fktm/app/water/SgEngine.kt` — 已经跑通的代码

## 任务目标

让 fkTM **自己**完成天猫校园的「手机号 + 短信验证码」登录，不依赖补丁版天猫校园。

拿到登录态之后，接水 / MV 开锁本来就能跑（`confong.cn` 域的 cookie2 就是 CM 身份，见阶段 4.5）。

## 已确认的事实（公理，不要重新验证）

1. **CM 身份 = `.confong.cn` 的 cookie2 单条**，和 taobao 域 cookie2 无关。有它就是 CM ✅。
2. **`x-sign` 已经复刻成功**：unidbg 产物与真机**逐字节一致**，`mtop.common.gettimestamp` 返回
   `SUCCESS::接口调用成功`。签名算法、22 段拼接、wua flag 全都对，不要再折腾签名。
3. **App 网关的 `smsSend` 只差 `x-sgext`**：
   - `x-sign + x-appkey/x-devid/x-ttid/x-utdid/x-umt` → `FAIL_SYS_PROTOPARAM_MISSED`
   - 再加一个**假**的 `x-sgext`（180 个 z）→ 协议层立刻放行，进风控层
   - 说明服务端只检查"有没有"，内容真伪留给风控
4. **`x-sgext` 只能由 SecurityGuard 的 middletier 产**：mtopsdk 的
   `SignConstants.MIDDLE_OUTPUT_X_SG_EXT = "x-sgext"`，来源是
   `IUnifiedSecurityComponent.getSecurityFactors()` → `doCommand(70102, …)`（12 个参数）。
5. **fkTM 进程内已经能跑天猫校园的 SG**（这是今天最大的进展）：
   ```
   getInstance ok in 429ms
   unified component = $Proxy11        ← middletier 插件加载成功
   ```
   实现见 `SgEngine.kt`，四个坑都写在代码注释里（InMemoryDexClassLoader、jniLibs、
   ContextWrapper 的 sourceDir/包名、getApplicationContext 返回自身、yw_1222.jpg 进 assets）。
6. **卡点只有一处**：middletier 的 `70101 init(authCode)` 校验
   - 空 → `2405`
   - 1~20 字符 → `2403`
   - ≥22 字符 → `2401`
   - **PC 的 unidbg 与真机 fkTM 行为完全一致** ⇒ 不是环境问题，是这个码的内容校验
   - 天猫校园自己跑的时候它一定过了，所以它有正确的 authCode
7. **appKey `34270422` 有效**（`4272` 这类会回 `FAIL_SYS_UNKNOWN_APP`）；
   但 `34270422` 和 `TmallCampusAndroid` 在天猫校园的 15 个 dex、90MB so、资源里**全都搜不到**，
   是运行时拿的 —— **静态提取 appSecret 这条路不通，别再搜了**。

## 已封死的路（有技术证据，不要重试）

| 路线 | 结论 |
|---|---|
| h5 网关 + 滑块 | 天猫校园发码**从不弹滑块**（官方客户端直接放行）；h5 滑过也是低信用会话。操作员明确否定 |
| `/gw` + `m-*` 老协议头 | 骨架是 `m-pv → m-appkey → m-sign → m-t(秒)`，齐了回 `ILEGAL_SIGN`。APK 15 个 dex 里 `m-sign` 只作为 `x-am-sign` 子串出现，天猫校园从不发 `m-*`，算法服务端独有 |
| 静态提取 appKey / appSecret | `34270422` 与 `TmallCampusAndroid` 在 15 个 dex、90MB so、资源里全都搜不到，运行时才拿到 |
| 猜 authCode | 空 / utdid / devid / 纯数字 / 32hex / base64 / JSON 全试过，只有 2401 / 2403 / 2405 |

**关于抓包**：mitmproxy（系统代理、iptables 重定向）、Reqable、tcpdump、frida hook `SSL_write`
这次都试过，没拿到 mtop 的明文。有用的结论是：mtop 走 tnet / cronet，明文只在 App 进程内部。
要拿请求内容就从进程内取（模块 / 进程内 hook / 在 fkTM 里跑 SG），不必在外围网络栈上花时间。

## 行为规矩（操作员原话级，违反会被直接叫停）

1. 改设备网络相关配置（iptables / 系统证书 / `http_proxy` / `adb reverse`）前，
   先说明影响范围、准备好回滚命令，做完立刻恢复。
2. 动设备前先请示（装 App、卸载、写 data 目录）。（本地 PC 实验可自主。）
3. 没验证过的事实不写进文档。
4. 每完成一个里程碑 → 更新 `PLAN-reverse.md` + `.workbuddy/memory/` 追加日志。
5. 长命令（mvn / jadx / IDA）放后台跑，别把会话卡死。
6. 失败路线写进「已封死」表，带证据，避免后人重踩。
7. 操作员已经很烦躁，回话要短、要具体、别铺陈。

## 建议的攻坚顺序

### 路线 A（首选）：在 fkTM 里枚举 SG 的命令码，找 sgext 的另一条产出路径
fkTM 进程内 SG 是**活的**（getInstance 成功）。可以写个探针，遍历 `doCommand` 的命令码
（20101~20199、70101~70199、5xxxx 等），把每个命令的返回值和长度打出来，
看有没有哪个命令直接吐出 `x-sgext` 形态的结果（base64，100~300 字符）。
**这条不依赖 authCode，值得先试。**

### 路线 B：让 fkTM 用 SG 的 signRequest / securityBody 组件，对比 unidbg 产物
确认真机环境产的 `x-sign` / `x-mini-wua` 与 unidbg 产的是否一致。
如果真机环境的 wua 更长/不同，说明 unidbg 那次只是"能跑"，形态可能不对。

### 路线 C：反汇编 `libsgmiddletierso-6.5.90.so` 找 70101 的判定
- 有现成 IDA 数据库：`tools/unidbg-tmall/libs/libsgmiddletierso-6.5.90.so.i64`
- IDA 路径：`E:/IDA9.4/idat.exe -A -S<脚本> <i64>`
- 已试过、**没有命中**：搜立即数 2401/0x961、2403/0x963、2405/0x965；搜 70101/0x111D5、70102/0x111E6
- `JNI_OnLoad` 在 0x87A0，**有控制流平坦化**（`BR X22` 混淆），静态跟很难
- doCommandNative 是 `RegisterNatives` 注册的，不在导出表里（导出只有 JNI_OnLoad / JNI_OnUnload）

### 路线 D（保底）：接受「必须有真实 authCode」，从 App 进程内部拿
只有模块（补丁版）/ 进程内 hook 能拿到。这条路等于回到补丁版方案，
但可以变成「模块只提供 authCode/sgext，fkTM 自己发请求」，体验仍比手动登录好。

## 现成资产与命令

**fkTM 侧**
- `water/SgEngine.kt` — SG 引擎（已跑通到 middletier init）
- `ui/SgProbeActivity.kt` — 自检入口：
  `adb shell am start -n com.fktm.app/.ui.SgProbeActivity`
  结果写 `/data/data/com.fktm.app/files/water_diag.txt`（`grep -a 'sg |' 或 'sgProbe'`）
- 设备上已就位：`/data/data/com.fktm.app/files/sg/tmall.apk`（天猫校园原包副本）+
  `files/sg/libs/`（它的 arm64 原生库）
- SG 的 so 在 `fktm-android/app/src/main/jniLibs/arm64-v8a/`，`yw_1222.jpg` 在 `assets/`

**PC 侧**
- `reverse/unidbg-harness/` — unidbg 工程。跑签名：
  ```
  mvn -s settings.xml -o -q compile exec:java -Dexec.mainClass=com.tmall.SignCli \
      -Dsg.apkmap=true -Dexec.args="--api=<api> --v=1.0 --datafile=<json> --t=<ms> ..."
  ```
  ⚠ 必须先 `compile`，`exec:java` 不触发生命周期；`MAVEN_OPTS` 要带 `--add-opens`
- `reverse/unidbg-harness/.../MiniTier.java` — middletier 70101/70102 探针
- `reverse/mtop_app_probe.py` / `reverse/sms_app_probe.py` — App 网关发请求（含 envelope 构造）
- `fktm/water/cmcheck.py` — 用 cookie 验 CM 是否生效
- 环境：JDK21 `C:/Program Files/Java/jdk-21`，Maven `D:/apache-maven-3.9.6/bin/mvn.cmd`，
  IDA `E:/IDA9.4/idat.exe`，设备 `dfe6d8a8`（adb 已是 root）

**已知的服务端返回（拿去对照）**
```
smsSend + 真 x-sign + ids            → FAIL_SYS_PROTOPARAM_MISSED::缺少协议参数
smsSend + 真 x-sign + ids + 假sgext   → FAIL_SYS_USER_VALIDATE（风控）
gettimestamp + 真 x-sign + ids        → SUCCESS
```

## 起手第一步

1. 读完上面三份文件
2. 跑一次自检确认环境还在：
   `adb shell am start -n com.fktm.app/.ui.SgProbeActivity`，然后
   `adb shell "grep -a 'sg |' /data/data/com.fktm.app/files/water_diag.txt | tail -8"`
   应该看到 `getInstance ok` + `init(<空>) 失败 code=2405`
3. 从**路线 A**开始：在 `SgEngine` 里加命令码枚举，看 SG 还能产什么

阻塞点如实记录，不要硬编结论。
