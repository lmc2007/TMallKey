<div align="center">

# TMKey

**一点即开 —— 天猫校园宿舍 BLE 门禁的独立开锁 App**

不打开天猫校园 · 不等开屏广告 · 不联网 · 点一下，门就开

[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![Python](https://img.shields.io/badge/Python-3.13-3776AB?logo=python&logoColor=white)](#)
[![Tests](https://img.shields.io/badge/tests-97%20passed-2ea44f)](#测试)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Network](https://img.shields.io/badge/network-none-lightgrey)](#安全与隐私)

</div>

---

TMKey 是一个**协议级复刻**项目：把天猫校园 App 里的宿舍 BLE 门禁开锁流程，
用一份不依赖原 App 的独立实现重写，并封装成一个开锁只需一次点击的 Android 应用。

整个开锁过程**不联网**，唯一凭证是每把锁独有的 `activationKey`。
本仓库**不含任何真实凭证**。

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
- **日志不泄漏密钥** —— 运行期刻意不打印派生的 `actualAesKey`（它与 `randomNumber` 一起出现即可反解出钥匙）。
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
│   ├── cli.py                    scan / unlock / battery / verify / selftest
│   ├── logparse.py               从 logcat 中复原 activationKey
│   └── p0_capture.bat            logcat 取证脚本
│
└── fktm-android/               Android App（Kotlin + Jetpack Compose）
    └── app/src/main/java/com/fktm/app/
        ├── protocol/             协议层（Kotlin 版）
        ├── ble/                  连接管理、连接复用与预连接
        ├── credential/           凭证存储（Keystore 加密）与 logcat 抓取
        └── ui/                   Compose 界面
```

> Kotlin 包名沿用项目早期的 `com.fktm.app`，尚未与仓库名统一。

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
./gradlew testDebugUnitTest      # 单元测试 43 项
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/
```

---

## 测试

| 套件 | 覆盖内容 | 用例数 | 状态 |
|---|---|---|---|
| `fktm/cli.py selftest` | Python 协议层 + 离线开锁仿真 + 黄金向量 | 54 | 通过 |
| `VocProtocolTest` | Kotlin 协议层（AES / 组帧 / 解帧 / 帧长判定 / 跨语言一致性） | 27 | 通过 |
| `LogcatParserTest` | logcat 行解析 | 10 | 通过 |
| `ShizukuHitFileTest` | 抓取结果文件的有效性判定 | 6 | 通过 |

协议层的任何改动，都必须让上述四套同时保持全绿。

> 测试中使用的凭证均为**假值**（`DEADBEEF…`）。
> 断言同样是逐字节锁定，只是向量由假钥匙合成，而非取自真机报文 —— 原因见下节。

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
