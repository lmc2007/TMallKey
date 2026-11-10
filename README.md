<div align="center">

# TMallKey

**一点即开 —— 天猫校园宿舍门禁 + 直饮水，脱离原 App 独立运行**

不打开天猫校园 · 不等开屏广告 · 开锁全程离线 · 点一下，门就开

[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](#)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

---

TMallKey 是一个**协议级复刻**项目：把天猫校园 App 里最常用的两件事，换成不依赖它的独立实现。

- **开锁** —— 宿舍 BLE 门禁，握手协议按字节复刻。**全程不联网**，唯一凭证是每把锁独有的
  `activationKey`，拿到一次就永久有效，飞行模式下也能开门。
- **接水** —— 直饮水机远程解锁。扫机器上的二维码（或填设备号）→ 云端下发解锁 →
  按机器上的「出水」键 → 超时后自动按 **0 元**结单。接水本身不花钱。

顺带把两件事的**前提**也一起解决了：门锁凭证与天猫校园登录态都能一键抓取，
不需要导出文件、不需要电脑（也提供 root / adb 兜底）。

<details>
<summary><b>English abstract</b></summary>

> TMallKey is an independent reimplementation of two flows inside the Tmall Campus app:
> (1) the dormitory BLE door lock, whose handshake is fully offline and gated only by a
> per-lock `activationKey`; (2) the drinking-water dispenser, unlocked through the campus
> cloud API and settled at zero cost. The repo ships a Kotlin/Android app plus a Python
> toolchain (protocol layer, offline lock simulator, `bleak`-based CLI).
> No real credentials are included.

</details>

---

## 目录

- [功能](#功能)
- [核心特性](#核心特性)
- [工作原理](#工作原理)
  - [门锁协议](#门锁协议)
  - [接水流程](#接水流程)
- [性能](#性能)
- [项目结构](#项目结构)
- [开发文档（DEVDOC）](DEVDOC.md)
- [快速开始](#快速开始)
- [使用教程](#使用教程)
  - [第 1 步 · 拿到凭证](#第-1-步--拿到凭证)
  - [第 2 步 · 导入凭证](#第-2-步--导入凭证)
  - [第 3 步 · 开锁](#第-3-步--开锁)
  - [第 4 步 · 接水](#第-4-步--接水)
  - [抓不到凭证？按这个顺序排查](#抓不到凭证按这个顺序排查)
- [测试](#测试)
- [安全与隐私](#安全与隐私)
- [已知限制](#已知限制)
- [路线图](#路线图)
- [常见问题](#常见问题)
- [贡献](#贡献)
- [许可证](#许可证)
- [免责声明](#免责声明)

---

## 功能

一句话：**门能开、水能接，前提（凭证与登录态）也一并解决。**

| 能力 | 做什么 | 前提 |
|---|---|---|
| **宿舍门锁（VOC）** | 一次点击离线开锁，冷启约 1.2 s、连接复用约 0.4 s；支持桌面快捷方式与快捷设置磁贴 | 该锁的 `activationKey` + BLE 地址 |
| **掌门神通锁（MV）** | 同样一键。没有静态钥匙，开锁帧由云端现取 | 登录态（每次开锁需联网） |
| **扫码接水** | 扫饮水机二维码或填设备号 → 云端解锁 → 按机器上的「出水」键 → **0 元**自动结单 | 登录态 |
| **凭证抓取** | 补丁版自动同步（免 root）/ root 一键 / 电脑 adb，三种方式 | 只有 root 方式需要 root |
| **登录态获取** | 补丁版同步 / root 读 cookie / 手机号 + 短信验证码（实验）/ 淘宝账号登录 | — |
| **应用内更新** | 6 个镜像逐级回退，下载后校验 sha256，同签名直接覆盖安装 | — |

> 门锁与饮水是两条**独立**链路：开锁**不需要**登录态（纯离线）；接水**必须有**登录态（走云端）。

---

## 核心特性

- **门锁完全离线** —— VOC 开锁不发起任何网络请求，飞行模式下可用。
- **顺带接管接水** —— 同一份登录态，扫一下机器二维码就能远程解锁，接水按 0 元自动结单。
- **两种锁都认** —— VOC（离线，认 `activationKey`）与掌门神通 MV（在线取帧）走同一个开锁入口。
- **一次点击** —— 冷启动自动预连接 + 开锁后 60 s 连接保活，二次开锁稳定在亚秒级。
- **不依赖原 App** —— 不 hook、不注入、不读取天猫校园进程，协议按字节复刻。
- **凭证与登录态都加密** —— 门锁 `activationKey` 与饮水 `cookie2` 都由 Android Keystore 的
  AES-256-GCM 加密后落盘，密钥不可导出、随卸载销毁。
- **可离线验证** —— 内置门锁仿真器，没有真机也能跑通完整开锁流程与协议回归。
- **双语言互为回归** —— 协议层同时有 Python 与 Kotlin 两份实现，黄金向量逐字节对齐。
- **凭证获取免 root** —— 装 LSPatch 补丁版天猫校园（发布流水线自动生成），在它里面点一次开锁，
  门锁凭证与饮水登录态一起经本地回环 Socket 同步进 TMallKey；也可走 root / adb 两种传统方式。

---

## 工作原理

两条链路彼此独立：**门锁走 BLE（离线）**，**接水走云端 mtop（需要登录态）**。

### 门锁协议

门锁是 **VOC 品牌 BLE 蓝牙锁**，开锁握手只有三步：

```
  TMallKey App                                    门锁
      │                                          │
      │ ────  0D  请求随机数（明文固定帧） ──────▶ │
      │ ◀───────────  16 字节 randomNumber  ───── │
      │                                          │
      │ ────  13  建立加密链路（明文固定帧） ────▶ │
      │ ◀───────────  密文 ack（需解密）  ─────── │
      │                                          │
      │ ────  0B  开锁（密文帧，内容为空） ─────▶ │
      │ ◀───────────  密文 ack 或裸 ack  ──────── │
      │                                          ▼
      │                                        门开
```

- **密钥派生**：`actualAesKey = randomNumber XOR activationKey 的后 16 字节`
- **加密算法**：`AES-128 / ECB / NoPadding`
- **开锁负载**：为空（明文仅为 `FF | 0B | 0000 | cksum | 补零`）

### 帧格式

两种帧靠 `55AA` 之后的第一个字节区分：

| 帧型 | 结构 | 用途 |
|---|---|---|
| 明文帧 | `55AA \| FF \| cmd \| len(2B,BE) \| payload \| cksum` | `0D` 请求随机数、`13` 建立链路、裸 ack |
| 密文帧 | `55AA \| len(2B,BE) \| cipher \| cksum` | `0B` 开锁、`01` 电量（响应亦为密文） |

`cksum = 帧内 cksum 之前所有字节之和 mod 256`。

### 接水流程

饮水机**不是** BLE 设备，走的是天猫校园云端 mtop 接口 —— 所以必须有登录态。

```
  TMallKey                                  天猫校园云端              饮水机
      │                                          │                     │
      │ ── device.water.start(deviceId) ────────▶ │                     │
      │ ◀────────── 已解锁 ────────────────────── │ ──── 远程指令 ─────▶ │
      │                                          │                     │
      │            （人走到机器前，按机器上的「出水」键）                  │
      │                                          │ ◀──── 出水 ──────── │
      │                                          │ ◀── 超时 / 手动结束 ─│
      │                                          │     按 0 元结单      │
```

- 解锁只调一个接口：`mtop.tmall.campus.share.applet.device.water.start`；
  按「暂停/结束」走 `device.water.stop`，不操作则机器超时后自动按 **0 元**结单。
- `deviceId` 填的是资源号 `resNo`（如 `630102040`），不是订单号；它来自机器上的二维码。
- 固定参数：`isv=CAMPUS`、`businessType=WASH_AND_CARE`、`paymentChannel=TMXY_APP`、
  `washDeviceType=DRINKING_MACHINE`。
- 身份 = 天猫校园 `.confong.cn` 域的 `cookie2`，有效期约 24 天。
- **接水免费；洗澡要钱**，后者本项目不碰。

---

## 性能

在同机型、同一把锁上实测（从点击到门锁响应）：

| 场景 | 耗时 |
|---|---|
| 冷启动首次开锁 | **≈ 1.23 s** |
| 60 s 内二次开锁（连接复用） | **≈ 0.37 s** |
| 优化前基线 | ≈ 3.0 s |

优化手段：按帧头精确计算帧长以取代 200 ms 空闲判帧、连接复用、前台预连接。
瓶颈已从协议交互（约 0.9 s）转移到 BLE 链路建立（约 4.9–7.9 s，仅在冷连接时出现）。

---

## 项目结构

```
TMallKey/
├── fktm/                       PC 侧工具链（Python 3.13）
│   ├── voc_protocol.py           协议复刻层，函数名与反编译出的 Java 原方法一一对应
│   ├── mock_lock.py              离线门锁仿真器（SimulatedLink）
│   ├── ble_client.py             基于 bleak 的真实 BLE 客户端
│   ├── cli.py                    selftest / frames / simulate / verify / scan / unlock
│   ├── logparse.py               从 logcat 中复原 activationKey
│   └── p0_capture.bat            Windows 下的 logcat 一键取证脚本
│
├── update/                     应用内更新清单（客户端直接读取）
│   ├── stable.json               当前版本、下载地址与 SHA-256
│   └── changelogs/               每个版本的更新日志，按 versionCode 命名
│
├── .github/                    发布流水线
│   ├── workflows/                Prepare Version Update / Release APK
│   └── scripts/                  清单校验与登记脚本
│
└── fktm-android/               Android App（Kotlin + Jetpack Compose）
    └── app/src/main/java/com/fktm/app/
        ├── protocol/             协议层（Kotlin 版，零 Android 依赖）
        ├── ble/                  连接管理、连接复用与预连接
        ├── credential/           凭证存储（Keystore）、logcat 抓取与抓取前台服务
        ├── mv/                   掌门神通锁（MV）在线开锁
        ├── water/                饮水：CM 会话、模块同步、mtop 客户端
        ├── vm/                   UnlockViewModel / WaterViewModel
        ├── update/               应用内检查更新与下载安装
        └── ui/                   Compose 界面
```

> Android 包名沿用早期的 `com.fktm.app`，尚未与仓库名统一。


---

## 快速开始

```bash
git clone https://github.com/lmc2007/TMallKey.git
cd TMallKey
```

### PC 侧工具链

依赖 Python 3.13：

```bash
cd fktm
pip install pycryptodome bleak

python cli.py selftest                                    # 协议自检 54 项 + 离线开锁仿真
python cli.py scan                                        # 扫描附近的 ~vTmallLock
python cli.py unlock --key <activationKey> --address <MAC>
python cli.py battery --key <activationKey> --address <MAC>
```

`mock_lock.py` 提供完整的离线门锁仿真，无需任何硬件即可验证协议实现。

### Android App

要求 JDK 17+ 与 Android SDK（compileSdk 36）：

```bash
cd fktm-android
./gradlew testDebugUnitTest      # 单元测试 83 项
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/
```

---

## 使用教程

两条线可以分开用，也可以一起用 —— **开门**要门锁凭证，**接水**要登录态。

| 你想做的事 | 需要准备 |
|---|---|
| 开门 | 一部 Android 手机 + 一扇**你本人有权开启**的门锁 + 能正常开锁的天猫校园 |
| 接水 | 一台直饮水机 + 天猫校园登录态（可以从原 App 直接搬过来） |

| 材料 | 要求 |
|---|---|
| 手机 | Android 8.0（API 26）以上，具备蓝牙 |
| 天猫校园 | 已登录；开门还需已绑定该门锁、能正常开锁 |
| TMallKey | 从[下载页](https://lmc2007.github.io/TMallKey/)或 [Releases](https://github.com/lmc2007/TMallKey/releases) 下载 APK 安装 |
| 补丁版天猫校园 | 免 root 取凭证 / 登录态**推荐**（与官方版同包名，只能装一个）—— 见下文与下载页 |

> **门锁凭证是两样东西**
> ① `activationKey` —— 32 位十六进制，等价于这把锁的钥匙；
> ② 门锁的 BLE 地址 —— 形如 `AA:BB:CC:DD:EE:FF`。

> **不想在电脑上看的**：App 底部「关于」页就有补丁版教程和镜像下载按钮，
> 门锁页的「+ 添加门锁凭证」面板顶部也有同样的入口；每次启动会自动检查更新，
> 有新版本会直接弹窗。


---

### 第 1 步 · 拿到凭证

推荐**补丁版天猫校园（免 root）**；手机已 root 的话走 **root 一键抓取** 更省事
（一次抓齐钥匙和饮水登录态）；实在不行动用 **电脑 adb** 排障。

> **注意：只打开门锁页是拿不到钥匙的。** 列表接口不下发 `activationKey`，
> 它只在**点「开锁」那一刻**由页面传给原生层 —— 第一次必须真的点一次开锁（门开一下就行）。

#### 补丁版天猫校园（免 root，推荐）

从 [Releases](https://github.com/lmc2007/TMallKey/releases/latest) 下载
**`tmcampus-5.0.0-lspatched.apk`**（天猫校园 · LSPatch 补丁版），
或到[下载页](https://lmc2007.github.io/TMallKey/)点「下载补丁版天猫校园」。

国内直连 GitHub 慢时用加速镜像（与 App 内「关于 → 镜像下载」是同一个地址）：

```
https://gh-proxy.com/https://github.com/lmc2007/TMallKey/releases/latest/download/tmcampus-5.0.0-lspatched.apk
```

**① 安装**

它与官方天猫校园**同包名、只能装一个**：手机里已装官方版的，先卸载
（登录态会重置，补丁版里重新登录即可）。

**② 登录并点一次开锁（这步不能省）**

打开补丁版天猫校园，用手机号 + 验证码登录（与官方版同一账号），
进入门锁页，**正常点一次「开锁」**，等门真的打开。

只打开门锁页是不够的：钥匙（`activationKey`）不是列表接口下发的，
而是点「开锁」时页面通过 `VocLockHelper.unlock(...)` 传给原生层的 ——
所以第一次必须真的开一次门。之后就一直用 TmallKey，不用再碰它。

**③ 回到 TMallKey，门锁已在列表里**

切到 TMallKey 的门锁页，新锁会**自动出现在列表里**
（默认名「门锁 + MAC 尾号」，可改名）。凭证与官方版抓到的完全一致 ——
都是离线 BLE 静态数据，一点即开。

> 打包命令与细节见 [`lspatch-module/README.md`](lspatch-module/README.md)。

#### root 一键抓取（需 root）

1. 在 KernelSU / Magisk 里给 TMallKey 授权（首次点抓取会自动弹框）；
2. TMallKey → 门锁页 → 「+ 添加门锁凭证」→ 点「**root 抓取**」；
3. **立刻**切到天猫校园点一次开锁 —— 钥匙自动填入表单，饮水登录态也同时导入
   （接水功能立即可用，不用再去饮水页单独导入）。

> 两个必须记住的点：
> **① 点完抓取别磨蹭** —— 天猫校园刷屏极凶，VOC 日志在缓冲区里活不过 1 分钟；
> **② 必须是一次「新会话」开锁** —— 如果刚才已经开过一次门，先把天猫校园**强制停止**
> 再重进，否则它走 `reconnect()` 复用会话，压根不会打印钥匙。

#### 电脑 adb 抓取（排障用）

适合没有 root、也不想换掉官方天猫校园时的排障手段。

**① 准备**

1. 手机打开「开发者选项」→「USB 调试」；
2. 装好 [platform-tools](https://developer.android.com/tools/releases/platform-tools)（即 adb）；
3. 数据线连上电脑，执行 `adb devices`，手机上确认「允许 USB 调试」——
   能看到设备序列号即可。

**② 开始抓取**
下载本仓库的`fktm\p0_capture.bat`
Windows 直接双击 `fktm\p0_capture.bat` 即可（它会自动清空日志缓冲、开始抓取、
结束后自动跑校验）。其他平台手动执行下面两条：

```bash
adb logcat -c
adb logcat -v time \
  VocLockHelperV2:V VocLockHelper:V VocLockJsBridge:V VocUnlockBridge:V \
  BluetoothHelper:V VocLockManager:V VocLockSession:V VocLockBridge:V \
  ZMUnlockBridge:V VocStatistics:V VocLogger:V Logger:V TLog:V \
  '*:S' > p0_capture.txt
```

**③ 去开一次门**

保持数据线连接（或者用 `adb tcpip` 走无线），走到门口用天猫校园点一次开锁，等门打开。

**④ 停止抓取，读出凭证**

回到电脑按 `Ctrl+C` 停止或关闭进程，
 `fktm/credentials.json`即为TMallkey可识别的JSON
可以进入下一步

也可以:
```bash
cd fktm
pip install pycryptodome
python cli.py verify --log p0_capture.txt
```

输出里直接就有你要的两项：

```
activationKey : 0123456789ABCDEF0123456789ABCDEF
macAddress    : AA:BB:CC:DD:EE:FF
...
```
  > **日志里没打印 `activationKey` 也能拿到。**
  > 密钥派生是可逆的 —— `actualAesKey = randomNumber XOR activationKey`，
  > 而 `randomNumber`（0D 响应明文）和 `actualAesKey` 日志里都有。
  > `logparse.py` 会自动反解，并且要求**多组独立会话解出同一个值**才采信，
  > 解不一致会拒绝下结论。

#### 怎么选（拿门锁凭证）

| | 补丁版 · 免 root | root 抓取 | 电脑 adb |
|---|---|---|---|
| 需要电脑 | 否 | 否 | 是 |
| 需要 root | 否 | **是** | 否 |
| 需要换掉官方天猫校园 | **是**（同包名二选一） | 否 | 否 |
| 同时拿饮水登录态 | **是**（登录即带过来） | **是** | 否 |
| 适用 | 绝大多数用户（推荐） | 已 root 的手机 | 排障 |


---

### 第 2 步 · 导入凭证

在 TMallKey 首页点「**+ 添加门锁凭证**」，有三种填法：

- **手工填写** —— 「名称」（默认「宿舍门」）、「门锁地址」、`activationKey`，
  然后点「保存」。地址必须是 `AA:BB:CC:DD:EE:FF` 格式，钥匙必须是 32 位十六进制。
- **粘贴 JSON** —— 直接粘贴 `fktm/credentials.json` 的**整段内容**，点「解析并导入」。
  同时支持完整结构（带 `locks` 数组）和单个凭证对象两种形式：

  ```json
  {
    "locks": [
      {
        "name": "宿舍门",
        "address": "AA:BB:CC:DD:EE:FF",
        "activationKey": "0123456789ABCDEF0123456789ABCDEF"
      }
    ]
  }
  ```

- **自动同步** —— 补丁版天猫校园里点一次开锁后，钥匙会自动出现在锁列表里；
  root 抓取则把钥匙填进表单，确认后点「保存」。

凭证保存在本机 Android Keystore 的加密区

---

### 第 3 步 · 开锁

回到首页，点卡片中间那个大按钮即可。首次会申请蓝牙权限，允许一次即可。

- 冷启动首次开锁约 **1.2 秒**，60 秒内再次开锁约 **0.4 秒**；
- 页面底部的「收发时序」会逐条显示收发字节，方便确认每一步都通了；
- 「查电量」按钮可以顺便读门锁电量；

连接**不是常驻**的，按两条计时规则自动回收 ——
**进入前台先自动预连接，30 秒内没有开锁就断开**；**一旦开锁则保活 60 秒**，
期间再开锁会重新计时；退到后台（含息屏）立即让出。
这是有意为之：把门锁的并发连接让给真正要用的人，下次开锁会自动重新连上。
首页顶部的状态角标（● 已预连接 / ◌ 预连接中 / ○ 未连接）可以直接看到当前状态。

---

### 第 4 步 · 接水

接水**不需要门锁凭证**，只需要天猫校园的**登录态**。第一次进「饮水」页会引导你选一种拿法：

| 方式 | 适合 | 说明 |
|---|---|---|
| **补丁版自动同步** | 已装补丁版天猫校园 | 在里面登录过一次即可，回 TMallKey 自动带过来，不用点导入 |
| **root 读一次 cookie** | 已 root、装的是官方版 | 从 WebView 明文 SQLite 里读一次，之后加密存本机，**不再需要 root** |
| **手机号 + 短信验证码** | 以上都没有 | 实验性：要过阿里滑块，成功率看风向 |
| **淘宝账号登录** | 手机号走不通 | 内嵌官方登录页，只收 cookie，密码本应用看不到 |

拿到登录态后：

1. 进「饮水」页，扫饮水机上的**二维码**（扫不出来就手动填设备号）；
2. 点「**解锁接水**」，机器响一声即已解锁；
3. 走到机器前，按机器自己的「**出水**」键接水；
4. 接完按「暂停/结束」；忘了按也没关系，机器超时后按 **0 元**自动结单。

> 登录态有效期约 **24 天**。过期后接水会提示未登录，按上表重新拿一次即可。
> 接水免费；**洗澡要钱**，本项目不涉及。

---

### 抓不到凭证？按这个顺序排查

**先确认一件事：天猫校园这次开锁到底有没有成功？**
门必须真的开了，才有日志可抓。先别急着怀疑 TMallKey。

还有一个前提容易被忽略：**钥匙只在「新会话」开锁时才会被打进日志**。
天猫校园进程活着、会话被复用时点开锁走的是 `reconnect()`，那条带 `activationKey` 的日志
**压根不会产生**，等多久都没用。所以顺序永远是：**先点「抓取」，再去点开锁**。

**手机内 root 抓取**

| 现象 | 检查 |
|---|---|
| 一直转圈，最后超时 | 天猫校园刷屏极凶，VOC 日志在环形缓冲里活不过 60 s。点完抓取**立刻**去开锁，别隔几分钟 |
| 门开了但抓不到 | 大概率走了 `reconnect()`（复用会话，不打钥匙日志）。到系统设置里**强制停止天猫校园**，重进再点一次开锁 |
| 抓到的不是这把锁 | 缓冲里还留着更早那次开锁的日志。核对「门锁地址」再点保存；实在不行手工填 |

**电脑 adb 抓取**

| 现象 | 检查 |
|---|---|
| `adb devices` 为空 | 换线/换口；手机上确认「允许 USB 调试」弹窗 |
| 抓到的文件里没有 `activationKey` | 天猫校园必须先完成一次**成功的**开锁 |
| 有日志但没有钥匙行 | 同上，可能是 `reconnect()` 路径，杀掉天猫校园重进再开一次；也确认过滤标签写了 `VocLockHelperV2`（tag 精确匹配，`VocLockHelper` 匹配不到 `V2`） |
| `verify` 报缺少 pycryptodome | `pip install pycryptodome` |

**都不行**：换个时机再试一次。凭证抓取依赖天猫校园把日志打出来，
不同版本、不同系统策略下这块差异很大 —— 但只要成功抓到过一次，之后就永久有效。

---

## 测试

| 套件 | 覆盖内容 | 用例数 | 状态 |
|---|---|---|---|
| `fktm/cli.py selftest` | Python 协议层 + 离线开锁仿真 + 黄金向量 | 54 | 通过 |
| `VocProtocolTest` | Kotlin 协议层（AES / 组帧 / 解帧 / 帧长判定 / 跨语言一致性） | 27 | 通过 |
| `UpdateManifestParserTest` | 更新清单解析、资产挑选、下载地址解析、镜像源顺序 | 18 | 通过 |
| `LogcatParserTest` | logcat 行解析 | 10 | 通过 |
| `LockImportPlanTest` | 模块整包 → 凭证导入计划 | 8 | 通过 |
| `CookieMergeTest` | 模块同步的 cookie 合并 | 7 | 通过 |
| `ModuleEnvelopeTest` | 模块 Socket 报文解析 | 7 | 通过 |
| `WaterTest` | 饮水会话与账号打码 | 6 | 通过 |

Kotlin 侧合计 **83** 项。协议层的任何改动，必须 Python 与 Kotlin 两侧同时保持全绿；
两边数值不一致 = 移植已漂移。



---

## 安全与隐私

- **`activationKey` 就是钥匙。** 本仓库不包含任何真实凭证。
- **App 侧**：门锁凭证与饮水登录态（cookie）都以 Android Keystore 的 AES-256-GCM 加密后存入
  SharedPreferences；Keystore 密钥不可导出，随卸载销毁；Backup 与设备迁移已通过
  `data_extraction_rules` 排除。
- **登录态只留在本机。** 饮水的 `cookie2` 等价于校园身份：不上云、不外传、不写日志
  （日志里只出现打码后的账号）。
- **测试侧**：所有向量均由假钥匙合成，断言依然逐字节锁定协议行为。

---

## 已知限制

- **厂商后台冻结**：ColorOS 等激进的后台管理策略会冻结已进入后台的进程，
  导致依赖持续读取 logcat 的「一键抓凭证」功能在部分机型和场景下不稳定。
  开锁功能本身不受影响。
- **抓取依赖天猫校园打日志**：`VocLockHelperV2` 只在**新会话**开锁时打印 `activationKey`，
  复用会话的 `reconnect()` 路径不打；而且天猫校园自身刷屏极凶，这些日志在环形缓冲里存活不到 1 分钟。
  所以抓取窗口内必须完成一次「新会话」开锁 —— 顺序永远是「先点抓取，再去开锁」。
- **接水依赖天猫校园云端**：需要登录态（约 24 天过期），走的是官方 mtop 接口 ——
  接口或风控一旦变更就可能失效，需要重新适配。这一点和门锁不同：门锁拿到钥匙就永久有效。
- **接水只覆盖直饮水**：按 0 元自动结单；洗浴等按量计费的设备不在支持范围内。
- **BLE 并发**：同一把锁可被两台设备同时连接（实测并发数 ≥ 2），
  但并发会显著拖慢链路建立（冷连接由约 2 s 劣化至约 7.6 s）。
  App 因此采用「退到后台即让出连接」的策略。
- **平台**：目前仅提供 Android 实现。

---

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 现场取证与真机验证 | 已完成 |
| P1 | 协议复刻（纯离线，无需设备） | 已完成 |
| P2 | BLE 客户端原型（真机开锁成功） | 已完成 |
| P3 | Android App 首版 | 已完成 |
| P4 | 提速（帧长判定 / 连接复用 / 预连接） | 已完成 |
| — | 冷启动预连接（30 s 窗口）与开锁后保活（60 s） | 已完成 |
| — | 桌面快捷方式 / 快速设置磁贴 | 已完成 |
| — | 掌门神通 MV 锁（云端取帧 → 写 AFF1） | 已完成 |
| — | 饮水：扫码解锁、按 0 元自动结单 | 已完成 |
| — | 登录态：补丁版同步 / root / 手机号短信 / 淘宝 | 已完成（短信为实验性） |
| — | 桌面小组件 | 计划中 |
| — | 凭证口令加密导出与迁移 | 计划中 |

---

## 常见问题

**需要 root 吗？**
不需要。开锁只要 BLE 权限，接水只要登录态；凭证和登录态都能走补丁版天猫校园自动同步（免 root）。
root 只是「从官方版里读一次 cookie」的捷径，读完就不再需要。

**需要联网吗？**
开门不需要 —— 开锁全程离线，飞行模式下可用。**接水必须联网**（走天猫校园云端接口），
应用内检查更新也要网络。两条链路互不影响。

**接水要钱吗？**
不要。直饮水机在 TMallKey 里按 **0 元**结单：解锁 → 按机器上的出水键 → 接完（或超时自动结束），
订单金额为 0。洗浴等按量计费的设备不在支持范围内。

**没有 root 能接水吗？**
能。三条免 root 路径：补丁版天猫校园登录一次自动同步、手机号 + 短信验证码（实验性）、淘宝账号登录。

**门锁和接水是一回事吗？**
不是，两条独立链路。门锁 = BLE 离线协议，只认 `activationKey`；接水 = 云端 mtop，只认登录态。
没登录态也能开门，没门锁凭证也能接水。

**会修改或替换天猫校园 App 吗？**
TMallKey 本身不会 —— 它不注入、不 hook、不读取天猫校园进程。
但免 root 取凭证需要装**补丁版天猫校园**（LSPatch 打包，与官方版同包名只能二选一），
那是个可选步骤，不装也能用 root / adb 方式拿凭证。

**凭证泄露会怎样？**
`activationKey` 一旦泄露，配合门锁的 BLE 地址即可离线开门。这是本项目的核心风险，
也是仓库不收录任何真实凭证的原因。

**支持哪些设备？**
Android 8.0（API 26）及以上，且具备 BLE 硬件。

---

## 贡献

欢迎提交 Issue 与 Pull Request。提交前请确认：

1. `python fktm/cli.py selftest` 全绿；
2. `./gradlew testDebugUnitTest` 全绿；
3. 未引入任何真实凭证、门锁地址或第三方反编译产物。

---

## 许可证

本项目采用 [MIT License](LICENSE)，Copyright (c) 2026 Muleng。

---

## 免责声明

- 本项目仅供**学习与安全研究**使用，仅适用于**本人有权开启的门锁**。
- **严禁**用于开启他人门锁，或任何未获授权的场景。
- 仓库**不包含也不提供**天猫校园 APK 及其反编译产物。
- 相关商标与软件著作权归各自权利人所有，本项目与之无从属或合作关系。
