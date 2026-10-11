<div align="center">

# TMKey

**一点即开 —— 天猫校园宿舍 BLE 门禁的独立开锁 App**

不打开天猫校园 · 不等开屏广告 · 不联网 · 点一下，门就开

[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](#)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

---

TMKey 是一个**协议级复刻**项目：把天猫校园 App 里的宿舍 BLE 门禁开锁流程，
用一份不依赖原 App 的独立实现重写，并封装成一个开锁只需一次点击的 Android 应用。

整个开锁过程**不联网**，唯一凭证是每把锁独有的 `activationKey`。

<details>
<summary><b>English abstract</b></summary>

> TMKey is a protocol-level reimplementation of the dormitory BLE door lock used inside the
> Tmall Campus app. The unlock handshake is fully offline and gated only by a per-lock
> `activationKey`. This repository ships a Kotlin/Android app plus a Python toolchain
> (protocol layer, offline lock simulator, `bleak`-based CLI). No real credentials are included.

</details>

---

## 目录

- [核心特性](#核心特性)
- [工作原理](#工作原理)
- [性能](#性能)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [使用教程](#使用教程)
  - [第 1 步 · 拿到凭证](#第-1-步--拿到凭证)
  - [第 2 步 · 导入凭证](#第-2-步--导入凭证)
  - [第 3 步 · 开锁](#第-3-步--开锁)
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

## 核心特性

- **完全离线** —— 开锁不发起任何网络请求，飞行模式下可用。
- **一次点击** —— 常驻连接 + 预连接策略，二次开锁稳定在亚秒级。
- **不依赖原 App** —— 不 hook、不注入、不读取天猫校园进程，协议按字节复刻。
- **凭证硬件级加密** —— `activationKey` 由 Android Keystore 的 AES-256-GCM 加密后落盘，密钥不可导出、随卸载销毁。
- **可离线验证** —— 内置门锁仿真器，没有真机也能跑通完整开锁流程与协议回归。
- **双语言互为回归** —— 协议层同时有 Python 与 Kotlin 两份实现，黄金向量逐字节对齐。

---

## 工作原理

门锁是 **VOC 品牌 BLE 蓝牙锁**，开锁握手只有三步：

```
  TMKey App                                    门锁
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

由此得出本项目的核心结论：**开锁报文里不含任何业务内容，
因此只要获得过一次 `activationKey`，即可对该门锁永久离线开锁。**

### 帧格式

两种帧靠 `55AA` 之后的第一个字节区分：

| 帧型 | 结构 | 用途 |
|---|---|---|
| 明文帧 | `55AA \| FF \| cmd \| len(2B,BE) \| payload \| cksum` | `0D` 请求随机数、`13` 建立链路、裸 ack |
| 密文帧 | `55AA \| len(2B,BE) \| cipher \| cksum` | `0B` 开锁、`01` 电量（响应亦为密文） |

`cksum = 帧内 cksum 之前所有字节之和 mod 256`。

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
        ├── protocol/             协议层（Kotlin 版）
        ├── ble/                  连接管理、连接复用与预连接
        ├── credential/           凭证存储（Keystore 加密）与 logcat 抓取
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
./gradlew testDebugUnitTest      # 单元测试 61 项
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/
```

---

## 使用教程

面向使用者的完整流程，**全程不需要 root**。

你需要的只有三样东西：一部 Android 手机、一扇**你本人有权开启**的门锁，
以及一个能正常开锁的天猫校园 App。

| 材料 | 要求 |
|---|---|
| 手机 | Android 8.0（API 26）以上，具备蓝牙 |
| 天猫校园 | 已登录、已绑定该门锁，能正常开锁 |
| TMKey | 从 [Releases](https://github.com/lmc2007/TMallKey/releases) 下载 APK 安装（首次需允许「安装未知应用」） |
| Shizuku | 只有「方式一」需要，见下文 |

> **凭证是两样东西**
> ① `activationKey` —— 32 位十六进制，等价于这把锁的钥匙；
> ② 门锁的 BLE 地址 —— 形如 `AA:BB:CC:DD:EE:FF`。
>
> 拿到这两样之后，对这把锁的每一次开锁都完全离线、永久有效。

---

### 第 1 步 · 拿到凭证

凭证只在天猫校园**开锁的那一瞬间**出现，所以下面两种方式本质上是同一件事：
**让天猫校园在你能看到的地方开一次锁。**

区别只在「谁在监听日志」——手机上的 TMKey 自己，还是你电脑上的 adb。

#### 方式一 · 手机 App 抓取（Shizuku）

最省事，不需要电脑。

原理：Android 4.1 起，普通 App 只能读自己进程的日志，而钥匙那行日志打在天猫校园
**自己的**进程里。Shizuku 提供了免 root 的 adb 通道，让 TMKey 以 shell 身份运行
`logcat` —— 与在电脑上敲 `adb logcat` 完全等价，抓到的内容也一模一样。

**① 装好 Shizuku 并让它运行起来**

从 [Shizuku 官网](https://shizuku.rikka.app/) 或
[GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 安装 Shizuku，
然后按你的设备情况启动它：

- **无线调试（免 root，Android 11+）** —— 推荐：
  1. 打开手机的「开发者选项」→「无线调试」；
  2. 在无线调试页里点「使用配对码配对设备」，记下 6 位配对码；
  3. 回到 Shizuku，点「通过无线调试启动」，按通知栏提示填入配对码。
- **Root** —— 若设备已 root，直接在 Shizuku 里点「通过 root 启动」。

Shizuku 启动成功后，它的首页会显示「正在运行」。**注意：重启手机后 Shizuku
会停止，需要重新启动一次**（无线调试方式尤其如此）。

**② 在 TMKey 里开始抓取**

1. 安装并打开 TMKey；
2. 点「**+ 添加门锁凭证**」，面板顶部就是「**自动抓取钥匙站**」；
3. 点「**开始抓取**」；
4. 第一次会弹出 Shizuku 授权框 —— 勾选「始终允许」并确定；
5. 授权后**再点一次「开始抓取」**（授权回调不会自动续跑，App 上也会提示这一步）。

此时界面会显示「正在后台监听日志」，最长监听 **4 分钟**，命中会立刻结束。

**③ 去开一次门**

1. 保持 TMKey 在后台（**别从最近任务里划掉它**）；
2. 切到天猫校园，走到门口；
3. 正常点一次「开锁」，等门真的打开。

**④ 回到 TMKey，保存**

切回 TMKey，表单会自动填入钥匙（日志里带地址时连地址一起填好）。
若提示「没有门锁地址」，手工把 `AA:BB:CC:DD:EE:FF` 形式的地址补上即可。
确认后点「**保存**」。

> **抓取中切走时 App 会被系统冻结**，这是正常的。
> TMKey 在服务端做了「命中即落盘」，只要门开过一次，回到 App 就会立刻取回结果 ——
> 界面短暂停在「抓取中」不用管，切回来就好。

#### 方式二 · 电脑 adb 抓取

适合没有装 Shizuku、或者手机端抓取一直不成功的情况。

**① 准备**

1. 手机打开「开发者选项」→「USB 调试」；
2. 装好 [platform-tools](https://developer.android.com/tools/releases/platform-tools)（即 adb）；
3. 数据线连上电脑，执行 `adb devices`，手机上确认「允许 USB 调试」——
   能看到设备序列号即可。

**② 开始抓取**

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

> ⚠️ `VocLockHelperV2` **必须**列出来：adb logcat 的标签是精确匹配的，
> 而真机走的是 V2，写 `VocLockHelper` 是匹配不到它的。

**③ 去开一次门**

保持数据线连接（或者用 `adb tcpip` 走无线），走到门口用天猫校园点一次开锁，等门打开。

**④ 停止抓取，读出凭证**

回到电脑按 `Ctrl+C` 停止，然后：

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
结论: 协议复刻与真机逐字节一致
```

> **日志里没打印 `activationKey` 也能拿到。**
> 密钥派生是可逆的 —— `actualAesKey = randomNumber XOR activationKey`，
> 而 `randomNumber`（0D 响应明文）和 `actualAesKey` 日志里都有。
> `logparse.py` 会自动反解，并且要求**多组独立会话解出同一个值**才采信，
> 解不一致会拒绝下结论。

#### 两种方式怎么选

| | 方式一 · 手机 App | 方式二 · 电脑 adb |
|---|---|---|
| 需要电脑 | 否 | 是 |
| 需要 root | 否 | 否 |
| 需要 Shizuku | **是**（并保持运行） | 否 |
| 主要风险 | 系统后台冻结导致结果延迟显示 | 过滤标签写错 / 天猫校园改版改标签 |
| 适用 | 日常、一次到位 | 排障、或不想装 Shizuku |

两种方式拿到的**是同一行日志**，随便选一个即可；抓不到时换另一个往往就好了。

---

### 第 2 步 · 导入凭证

在 TMKey 首页点「**+ 添加门锁凭证**」，有三种填法：

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

- **自动抓取** —— 见上面的「方式一」，抓到后会自动填进表单，再点「保存」。

凭证保存在本机 Android Keystore 的加密区，**不上云、不参与备份、随卸载销毁**。

---

### 第 3 步 · 开锁

回到首页，点卡片中间那个大按钮即可。首次会申请蓝牙权限，允许一次即可。

- 冷启动首次开锁约 **1.2 秒**，60 秒内再次开锁约 **0.4 秒**；
- 卡片上会显示收发时序，方便确认每一步都通了；
- 「查电量」按钮可以顺便读门锁电量；
- **全程不联网**，飞行模式下照样能开。

门锁离开 App 超过一段时间会断开连接，这是有意为之 ——
把门锁的并发连接让给真正要用的人，下次开锁会自动重新连上。
想更快的话：**保持 App 在前台**，第二次开锁会走连接复用。

---

### 抓不到凭证？按这个顺序排查

**先确认一件事：天猫校园这次开锁到底有没有成功？**
门必须真的开了，才有日志可抓。先别急着怀疑 TMKey。

**方式一（手机）**

| 现象 | 检查 |
|---|---|
| 提示「Shizuku 未运行」 | Shizuku App 是否显示「正在运行」；重启手机后需要重新启动 Shizuku |
| 停在「正在后台监听」不动 | 是否已经点过「开始抓取」**之后**才去开的门（抓取会先清空日志缓冲） |
| 一直抓不到 | 天猫校园是否真的完成了开锁；地址是否填对；换电脑 adb 方式交叉验证 |
| 抓到钥匙但没地址 | 日志里只有 key 没有 mac，手工填地址即可 |
| 长时间无结果 | 4 分钟后会自动结束并给出诊断信息 |

诊断信息：

```bash
adb logcat -s fkTM:D                                  # App 侧抓取日志
adb shell cat /data/local/tmp/fktm_grab_diag.txt      # 服务端进度（需 shell/root）
```

**方式二（电脑）**

| 现象 | 检查 |
|---|---|
| `adb devices` 为空 | 换线/换口；手机上确认「允许 USB 调试」弹窗 |
| 抓到的文件里没有 `activationKey` | 天猫校园必须先完成一次**成功的**开锁 |
| 有日志但没有钥匙行 | 确认过滤标签里写了 `VocLockHelperV2`；天猫校园改版可能改了标签，先全量捞一次：`adb logcat \| findstr /i activationKey` |
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
| `ShizukuHitFileTest` | 抓取结果文件的有效性判定 | 6 | 通过 |

协议层的任何改动，都必须让上述五套同时保持全绿。



---

## 安全与隐私

- **`activationKey` 就是钥匙。** 本仓库不包含任何真实凭证。
- **App 侧**：凭证以 Android Keystore 的 AES-256-GCM 加密后存入 SharedPreferences；
  Keystore 密钥不可导出，随卸载销毁；Backup 与设备迁移已通过 `data_extraction_rules` 排除。
- **仓库侧**：`.gitignore` 屏蔽 `credentials*.json`、`*capture*.txt` 及第三方 APK 与反编译产物。
  关键约束是 `actualAesKey = randomNumber XOR activationKey` ——
  **这两个数绝不能同时出现在仓库里**，否则等价于公开钥匙。
- **测试侧**：所有向量均由假钥匙合成，断言依然逐字节锁定协议行为。

---

## 已知限制

- **厂商后台冻结**：ColorOS 等激进的后台管理策略会冻结已进入后台的进程，
  导致依赖持续读取 logcat 的「一键抓凭证」功能在部分机型和场景下不稳定。
  开锁功能本身不受影响。
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
| — | 前台驻留与连接让出策略 | 已完成 |
| — | 一键抓取凭证（Shizuku） | 进行中 |
| — | 桌面快捷方式 / 小组件一键开锁 | 计划中 |
| — | 凭证口令加密导出与迁移 | 计划中 |

---

## 常见问题

**需要 root 吗？**
不需要。开锁只需要 BLE 权限；抓取凭证走 Shizuku（免 root），或使用 adb / root 作为备选。

**需要联网吗？**
开锁全程不联网，飞行模式下依然可用。

**会修改或替换天猫校园 App 吗？**
不会。TMKey 是完全独立的应用程序，不注入、不 hook、不读取其进程。

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
