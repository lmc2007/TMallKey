# P1 立项提示词（复制全文给新会话）

---

你在 TMallKey 项目工作区（C:/Users/64702/Desktop/SRC/TMSCHOOL）。先读三个文件再动手：
`PLAN-reverse.md`（总规划，你执行其中的 P1）、`DEVDOC.md`（协议细节）、
`.workbuddy/memory/` 下 2026-10-30 的日志（今天全部实验的原始记录）。

## 任务

逆向天猫校园 5.0 的 SecurityGuard native 层（wua/sgext 生成），让 fkTM 能对**任意会话**
完成 CM 层校园建档 —— 最终效果：免 root 用户（补丁版天猫校园）也能用接水和 MV 锁。

## 已实验闭环的事实（不要重新验证，直接当公理用）

1. CM 层校园身份绑在 `cookie2` 上；CM ✅ 的会话才能调 `guide.zmst.unlock.data.get`（MV
   开锁帧）和接水接口，否则分别报 CE003 / GET_CAMPUS_USER_ERROR。
2. 官方版 WebView 的 cookie2 只有在打开过校园页面（native mtop sync）后才是 CM ✅；
   补丁版登录的会话**永远** CM ❌ —— CM 建档发生在登录时，校验 SecurityGuard 生成的
   反作弊头（x-mini-wua / x-sgext），服务端按真实签名校验，补丁签名被拒。
3. `member.user.login` 不能补建档（实测 doHaveLogin:true 但 CM 层不变）。
4. 一切修复 cookie 的路线（3PcFlag 等）已二分排除，只看 cookie2。

## 技术路线：unidbg 模拟执行官方 SO

不逆算法，把官方 APK 的 SecurityGuard SO 搬进 unidbg 直接调，用**官方签名**（原包自带）
生成合法 wua → 用它调 CM 建档接口 → 任意会话变 CM ✅。

### 资产（已就位）

- `tools/unidbg-tmall/libs/`：libsgmainso-6.5.97（主入口壳）、libsgmiddletierso-6.5.90、
  libsgsecuritybodyso-6.5.104（wua 核心）、libsgcore/libsgmain/libsgmiddletier（loader）
- `tools/unidbg-tmall/assets/yw_1222.jpg`：SecurityGuard 加密数据文件
- `天猫校园.apk`：原包（官方签名来源）
- `tools/out2/` `tools/out3/`：反编译源码
- appKey：`12574478`；Java 入口：
  `com.alibaba.wireless.security.open.SecurityGuardManager`、
  `com.taobao.wireless.security.sdk.SecurityGuardManager`、
  `com.ali.user.mobile.security.SecurityGuardManagerWraper`
- 模板：unidbg 仓库 test 里的 `TTEncryptUtils.java`（bytedance）和
  `anjuke/mobile/sign/SignUtil.java` —— 加载商业 SO + JNI 的标准骨架

### 环境

- JDK 21：C:/Program Files/Java/jdk-21
- Maven 走代理：`-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=...`（MAVEN_OPTS）
- 操作员手机 dfe6d8a8（root，官方版天猫校园 5.0 已登录）—— **动设备前先请示**
- PC 端 mtop 测试链现成：`fktm/water/mtop.py`（Python）—— CM probe 用
  `mtop.tmall.campus.member.app.user.get`，❌=CM_FAIL_BIZ_USER_NOT_LOG_IN

### 里程碑（P1 阶段 1~4，逐个交付）

1. **SO 提取完成**（已做，见 tools/unidbg-tmall）
2. **unidbg harness**：clone github.com/zhkl0228/unidbg，仿 TTEncryptUtils 写
   `com/tmall/SGHarness.java`，目标 = libsgmainso-6.5.97 加载 + JNI_OnLoad 走通
3. **wua 产出**：调通 initialize（读 yw_1222.jpg，需要虚拟文件系统重定向到提取的 assets）
   → signRequest/getSecurityBodyDataEx 产出与真机抓包格式一致的 wua
4. **一致性验证**：同一会话、同参数，unidbg 产物 vs 真机请求抓包对比；服务端接受性验证
   （CM ❌ 会话 + unidbg wua 调建档 → CM 变 ✅ → zmst.unlock.data.get 通）
5. **fkTM 集成**：wua 生成器进 App（方案届时定：内嵌 unidbg 运行时或独立签名服务）

### 验收标准

最终 = 一个 CM ❌ 的补丁版会话，经 unidbg wua 建档后 `member.app.user.get` 变 ✅ 且
`guide.zmst.unlock.data.get` 返回 encryptData。中间每个里程碑先在 PLAN-reverse.md 的
P1 段落打勾并写明阻塞点，再继续。

## 行为规矩（操作员原话级要求）

1. **没有操作员批准，不许回复 GitHub issue 或任何公开渠道。**
2. **动手机设备（adb/su 写操作、装卸 App）前先请示；本地 PC 实验可自主。**
3. 没验证过的事实不要写进文档/文案/结论。
4. 每个里程碑完成 → 更新 PLAN-reverse.md P1 段落 + `.workbuddy/memory/` 追加日志。
5. 长命令（mvn 首次拉依赖等）放后台跑并汇报，不要让会话卡死。
6. 失败的路线写进「已封死」表，带日期和证据，避免后人重踩。

## 起手第一步

clone unidbg（走代理）→ 读它的 TTEncryptUtils / anjuke SignUtil 两个 test 骨架 →
写 com/tmall/SGHarness.java 加载 libsgmainso-6.5.97.so → JNI_OnLoad。
阻塞点如实记录，不要硬编。
