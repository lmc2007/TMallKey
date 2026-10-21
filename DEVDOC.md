# TMallKey 开发文档（DEVDOC）

> **读者**：要读懂、修改、移植这套协议的人。
> **不是**安装说明 —— 装 App 开自己宿舍门看 [README.md](README.md)。
> 本文只讲：协议怎么逆出来的、代码怎么分层、状态机怎么转、坑在哪、要扩展时改哪里。

---

## 目录

- [0. 一张图看懂](#0-一张图看懂)
- [1. 命名对照表](#1-命名对照表)
- [2. 协议层](#2-协议层)
- [3. Python 参考实现](#3-python-参考实现)
- [4. Android 实现](#4-android-实现)
- [5. 构建、测试与调试](#5-构建测试与调试)
- [6. 发布流水线](#6-发布流水线)
- [7. 扩展指南](#7-扩展指南)
- [8. 设计取舍](#8-设计取舍)
- [9. 安全边界](#9-安全边界)

---

## 0. 一张图看懂

三套东西常被搞混，先分清：

```
① 原始链路（天猫校园 App，黑盒）
   H5 页面 ──► VocLockBridge (JS bridge)
                 │
                 ▼
           com.tmall.campus.doorlock.VocLockHelperV2   ← 真机走的是 V2
                 │
                 ▼
           BluetoothHelper  ──► Android GATT ──► VOC BLE 门锁
                 │
                 └─► 日志里漏出 activationKey / randomNumber / actualAesKey

② 我们的复刻（逻辑等价，离线）
   activationKey（唯一凭证，每锁一个）
        │
        ▼
   fktm/voc_protocol.py   ← Python 参照实现，逐字节对齐真机
   fktm-android/.../VocProtocol.kt  ← 同一套算法的 Kotlin 移植
        │
        ▼
   GATT 写 20 字节分片 ──► 门锁

③ 本仓库两组实现的分工
   fktm/          离线验证用：自检、仿真、真机 CLI、日志取证
   fktm-android/  发布用：一键开锁 App（含凭证抓取）
```

**核心事实**：开锁**全程不联网**。门锁只认 `activationKey`，它就是一个纯本地可信凭证 —— 拿到它 = 永久有钥匙。

---

## 1. 命名对照表

这套东西有三套并存的名字，改代码前先对号，别改错。

| 位置 | 值 | 说明 |
|---|---|---|
| GitHub 仓库 | `TMallKey` | 仓库名 |
| Release 资产 | `TMKey-{versionName}.apk` | **CI 强校验**，写死 |
| App 显示名 | `TmallKey` | `strings.xml` |
| Python 包目录 | `fktm/` | 工具链 |
| Android 包名 | `com.fktm.app` | **不能改**，改了老用户无法覆盖安装 |
| Android 源码根 | `.../java/com/fktm/app/` | |
| 日志 TAG | `fkTM` / `fkTMBle` | `adb logcat -s fkTM:D` |
| 协议广播名 | `~vTmallLock` | 门锁 BLE 名，含此串 |
| 协议内部代号 | VOC | `VOC_BleSmartLock`、`VocLockHelper` |

> 命名不统一是历史包袱，**别顺手统一** —— 包名和资产名都牵动升级链。

---

## 2. 协议层

**唯一真相** = `fktm/voc_protocol.py`（469 行）。Kotlin 侧 `VocProtocol.kt` 是它的逐函数移植，两边常量必须保持一致。

### 2.1 传输与发现

| 项 | 值 |
|---|---|
| 广播名 | 含 `~vTmallLock` |
| Service UUID | `00002760-08c2-11e1-9073-0e8ac72e1001` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |
| 写特征 / 通知特征 | **UUID 不固定**，靠 properties 挑：写 = 含 `0x04` 或 `0x08`；通知 = 含 `0x10` |
| MTU | 23（默认，未协商）→ 每包 **20 字节** |
| 加密 | AES-128 / ECB / **NoPadding** |

⚠️ 特征 UUID 因固件批次不同会变，**任何写死 UUID 的实现都会在某批锁上直接失败**。必须遍历 service 的 characteristic 按 properties 匹配。

### 2.2 帧格式

两种帧型靠 `55AA` 后首字节区分（真机逐字节验证过）：

```
明文帧（请求 0D/13/0C，响应 0D）
  55AA | FF | cmd(1B) | len(2B, 大端) | payload(len 字节) | cksum(1B)
  总长 = 7 + len

密文帧（请求 0B/01/0E/06，响应 13/0B/01）
  55AA | len(2B, 大端) | cipher(len 字节) | cksum(1B)
  总长 = 5 + len
```

`cksum` = 校验码，`compute_validate_code()`：**逐字节求和 % 256**，输出 2 位 hex。
两种帧的求和范围都是「从 `55AA` 到校验位之前」：

- 明文帧：`cksum(55AA + FF + cmd + len + payload)`
- 密文帧：`cksum(55AA + len + cipher)`

例：`cksum("55AAFF0D0000")` = `0b` → 所以 `0D` 固定帧是 `55AAFF0D0000` + `0B`。

⚠️ 该函数输出**小写**，而常量表里全是大写 —— 拼帧时必须统一（我们的做法是拼完 `.upper()`）。

明文帧里的 payload 是「blob」，组装规则（`assemble_distribute_content()`）：

```
FF | sign(1B) | contentLen(2B, 大端) | content | cksum
然后补 "0" 对齐到 32 hex（= 16 字节）
```

对齐到 16 字节是硬要求 —— AES NoPadding 只吃 16 的整数倍。`content` 为空（开锁）时：

```
FF 0B 0000 09 | 00*22         ← 10 hex 有效 + 22 个 '0' 补齐到 32 hex = 16 字节
└─┬─┘ └┬┘ └─┬─┘ └┬┘
  FF   sign  len  cksum(55AAFF0B0000)
```

> 🔍 **移植陷阱**：前导是**字面量大写 `FF`**，不是长度字段的一部分；`xor()` 返回**小写**，常量表里是**大写** —— 比对前必须统一大小写。这两条各坑过一次。

### 2.3 三种命令的实际帧型

这是 P0 真机校正后的结论，**和早期从反编译常量表读出来的不一样**，别照抄常量表：

| 命令 | 含义 | 请求帧型 | 响应帧型 | 落地常量 |
|---|---|---|---|---|
| `0D` | 取随机数 | 明文固定 `55AAFF0D00000B` | **明文** 24 B | `FIXED_FRAMES` |
| `13` | 建立加密链路 | **明文固定** `55AAFF13000011` | **密文** 21 B | `FIXED_FRAMES` |
| `0B` | 开锁 | **密文**（content 空） | 密文 21 B（或裸 ack） | `build()` 现场生成 |
| `01` | 读电量 | **密文** | 密文 | `build()` 现场生成 |
| `0E` / `06` | 生成密钥 / 激活 | 密文 | 密文 | 激活阶段用 `DEFAULT_AES_KEY` |
| `0C` | 重置 | 明文（**未真机验证**） | — | ⛔ 见下 |

⛔ **`0C` 重置会作废 activationKey**。真机从没跑过，代码里保留但不接任何 UI。任何调试脚本都不要碰它。

表格里的 `CMD_IS_CLEAR_FRAME` 就是这张表的代码形态（`True` = 明文固定帧）。

### 2.4 密钥派生

只有一条公式，**全项目最关键的 3 行**：

```python
actual_key = xor(random_number, activation_key)
```

- `random_number`：`0D` 响应 payload 的后 16 字节（`payload = prefix(1B) + random(16B)`）
- `activation_key`：32 hex，`xor()` 只取**末 16 字节**（`hexString.substring(length - 32)`）
- 逐字节异或，得到 `actual_key`，直接当 AES-128 的 key

```
   randomNumber (16B)  XOR  activationKey[末16B]  =  actualAesKey (16B)
```

`DEFAULT_AES_KEY = "564F435F426C65536D6172744C6F636B"`（ASCII `VOC_BleSmartLock`）**只在激活/生成密钥阶段用**，日常开锁用不到。

> 💡 这条公式同时是**反解工具**：日志里只要同时出现 `randomNumber` 和 `actualAesKey`，就能算出 `activationKey`。所以我们的日志**永远不打印 `actualAesKey`**（见 4.9）。

### 2.5 握手时序

开锁只有三条命令，一条都不能少：

```
App / CLI                                    门锁
   │                                           │
   │  0D 明文 55AAFF0D00000B                   │
   │ ─────────────────────────────────────────►│
   │                                           │
   │  明文 55AA FF 0D 0011 <prefix><random16> cksum   (24 B)
   │ ◄─────────────────────────────────────────│
   │                                           │
   │  13 明文 55AAFF13000011                   │
   │ ─────────────────────────────────────────►│   ← 注意：请求是明文！
   │                                           │
   │  密文 55AA 0010 <cipher16> cksum          (21 B)
   │ ◄─────────────────────────────────────────│
   │                                           │
   │  0B 密文 (content 为空)                    │
   │ ─────────────────────────────────────────►│
   │                                           │
   │  密文 ack / 或裸 ack 55AAFF0B0001000A     │
   │ ◄─────────────────────────────────────────│
```

`13` 的请求是**明文固定帧**这件事最容易搞错 —— 早期实现按 `build()` 生成密文帧，真机上直接失败。**只有 `13` 的响应是密文**，请求是明文。

### 2.6 成功判定（双路径）

不同固件返回不一样，两条都要认：

```python
# 路径 1：裸 ack（早期固件，直接明文返回）
ack.upper().startswith("55AAFF0B0001000A")

# 路径 2：解密后看状态字节
plain = aes_decrypt(extract_message(ack), actual_key)
ok = plain.startswith("ff") and plain[8:10] == "00"
```

路径 2 里 `plain[8:10]` 是**状态码**（`00` = 成功），`plain.startswith("ff")` 是完整性校验（前缀丢了说明密钥错）。

任何一条命中就算成功。**两条都不命中时不要重试开锁** —— 可能是锁已开但 ack 丢了，重试会二次动作。

### 2.7 错误码

`CODE_MAP` 共 21 项（`00`–`14`），常用几个：

| 码 | 含义 | 排查方向 |
|---|---|---|
| `00` | 成功 | — |
| `02` | 超时 | 信号弱 / 帧长判定错 / 分片间隔太短 |
| `0a` | ID 不存在 | activationKey 不属于这把锁 |
| `0b` | 门锁未进入待激活状态 | 激活流程用，日常开锁不该出现 |
| `0c` | 系统锁定中 | 连续失败触发锁体保护，等一会儿 |

### 2.8 帧长判定（提速核心）

原始 App 用 **200ms 空闲**判断一条消息结束（`MESSAGE_READ_INTERVAL_MS`）。我们改成**按帧头算总长**：

```python
def expected_frame_length(buf):
    if buf[2] == 0xFF:                      # 明文：7 + payload_len
        return 7 + ((buf[4] << 8) | buf[5])
    return 5 + ((buf[2] << 8) | buf[3])     # 密文：5 + cipher_len
```

实测命中：`0D` 响应 `7 + 0x11 = 24` B，`13`/`0B` ack `5 + 0x10 = 21` B。

判定不出来时**回退到 60ms 空闲兜底**（`MESSAGE_FALLBACK_IDLE_MS`），不是死等 200ms。

这条优化是「3 秒开锁 → 1.2 秒」的主要来源之一，另外两个是末包不 sleep、连接复用。

### 2.9 反解 activationKey

抓不到明文 key 时的兜底：`logparse.recover_activation_key(capture)`。

```
activationKey = randomNumber XOR actualAesKey
```

日志里只要有 `0D` 响应帧和任意一条 `build/parse: <32hex>`（actualAesKey），就能还原。自检里有对应用例。

---

## 3. Python 参考实现

### 3.1 模块职责

| 文件 | 行数 | 职责 | 依赖 |
|---|---|---|---|
| `voc_protocol.py` | 469 | **纯协议**：常量、加解密、组帧/解帧、帧长判定。零 IO | pycryptodome |
| `ble_client.py` | 291 | 真机 BLE：扫描、连接、GATT 收发（bleak） | bleak、voc_protocol |
| `mock_lock.py` | 242 | **门锁仿真**：内存里跑一遍完整状态机，含时序模拟 | voc_protocol |
| `cli.py` | 588 | 命令行入口 + 自检（54 项） | 以上全部 |
| `logparse.py` | 258 | logcat 取证解析：重组分片、提取 key、逐字节校验 | voc_protocol |
| `make_synthetic_log.py` | 114 | 造合成日志（无真机时测 logparse） | voc_protocol |

设计原则：**协议层零 IO、零依赖设备**。所以能在 PC 上跑全量自检，不需要门锁在旁边。

### 3.2 数据流

```
            ┌──────────────┐
   CLI ────►│  voc_protocol │◄──── mock_lock（内存仿真）
            └──────┬───────┘
                   │ frame_hex
            ┌──────▼───────┐
   真机 ────►│  ble_client  │◄──── bleak（Windows/macOS/Linux 都能跑）
            └──────────────┘
```

`ble_client.BleVocLock` 是 async context manager，`unlock()` / `battery()` 返回 `(ok, ...)` 元组，失败带中文原因。

### 3.3 CLI 命令矩阵

```bash
python fktm/cli.py <cmd>
```

| 命令 | 需要门锁 | 需要凭证 | 用途 |
|---|---|---|---|
| `selftest` | ✗ | ✗ | 协议自检 54 项 + 离线开锁仿真 |
| `frames` | ✗ | ✗ | 打印 0D/13/0B 三帧 hex（调试对照） |
| `simulate` | ✗ | ✗ | 离线跑一遍开锁，打印完整时序 |
| `verify` | ✗ | 日志文件 | 解析 P0 取证日志，与复刻帧**逐字节**对照 |
| `scan` | ✓ | ✗ | 扫描附近 `~vTmallLock` |
| `unlock` | ✓ | ✓ | 真机开锁 |
| `battery` | ✓ | ✓ | 读电量 |
| `hold` | ✓ | ✗ | 持有一条连接 N 秒，测锁的并发上限 |

凭证来源（`_resolve_key` / `_resolve_addr`）：`--key` / `--addr` 参数 → 否则读 `fktm/credentials.json` 的**第一把锁**。没有环境变量这条路径，别猜。

`credentials.json` 结构：`{_README, recoveredAt, source, locks[]}`，每项含 `name/address/activationKey`。**这个文件绝不入库**（已在 `.gitignore`）。

### 3.4 自检的 54 项在测什么

`cli.py selftest` 按块组织，出问题能直接定位到层：

| 块 | 覆盖内容 |
|---|---|
| 基础工具 | 校验码、xor、MAC 格式化、广播解析 |
| 组帧/解帧 | 明文 blob 补位、密文帧结构、`extract_message` / `extract_data` |
| 密钥派生 | `random XOR activationKey`、边界（key 短于 16B） |
| 真机黄金向量 | `p0_capture.txt` / `p2_real_capture.txt` 里每一帧的**逐字节复现** |
| 帧长判定 | 10 个向量的精确长度、非法帧头、数据不足、分片缓冲（19 B 不完整 / 24 B 完整） |
| 仿真闭环 | `mock_lock` 跑通 0D→13→0B，正误 key 两种结局 |

**基线 54/54**。改协议层任何代码，先跑这个；改动 Kotlin 侧则跑 `testDebugUnitTest`（基线 61/61）。两边数值不一致 = 移植已漂移。

### 3.5 mock_lock 怎么仿真

`MockLock` 是一个纯内存状态机：收 `frame_hex` → 判帧型 → 解密 → 校验 sign 与密钥 → 返回响应帧。`SimulatedLink` 在外面套一层时序（模拟 RTT、分包），让 `unlock()` 的调用路径和真机**完全一致**。

好处：CI 里没有蓝牙也能测全流程；协议改动先在仿真里过一遍，再去门口试。

---

## 4. Android 实现

### 4.1 分层

```
ui/           MainScreen · UpdateCard · theme      (Compose，无业务)
vm/           UnlockViewModel                       (状态 + 编排)
──────────────────────────────────────────────────  ← 业务边界
ble/          BleLockClient                         (GATT + 连接生命周期)
protocol/     VocProtocol                           (纯算法，零 Android 依赖)
credential/   CredentialStore · LockCredential      (Keystore 加密存储)
              ShizukuLogcatGrabber · LogcatGrabService
              GrabForegroundService · LogcatParser
update/       AppUpdateRepository · UpdateManifest · ApkInstaller
```

依赖方向**单向向下**：`ui → vm → ble/credential → protocol`。`protocol/` 不 import 任何 `android.*`，所以能在 JVM 单测里跑。

### 4.2 连接状态机

`BleLockClient` 是**进程级单例**（`FktmApp.bleClient`），全 App 共用一条 GATT。

状态：

| 状态 | 判据 | 进入方式 |
|---|---|---|
| `Idle` | `!isReady && !isConnecting` | 初始 / `close()` |
| `Connecting` | `isConnecting` | `ensureConnected()` |
| `Ready` | `isReady && readyAddress == target` | 服务发现完成 |
| `Ready(other)` | `isReady` 但地址不符 | 换锁时先 `close()` 重连 |

事件 × 动作：

| 事件 | 前台 | 后台 |
|---|---|---|
| `setForeground(true)`（onStart） | 开**冷启动窗口** 30 s，到期无线程活动就断 | — |
| `setForeground(false)`（onStop/息屏） | — | `isBusy` 为假则**立即** `close()`；有任务在跑等收尾 |
| `beginTask()` | 取消空闲计时，`inFlight++` | 同左（任务中不退让） |
| `endTask()` | 前台 → 改挂 **60 s 开锁后保活**；后台 → 立即 `close()` | |
| 开锁成功 | 保活 60 s，期间再开锁**重新计时** | — |

```
onStart ──► [冷启动窗口 30s] ──到期──► close()
                  │
                  └─ 用户点开锁 ──► beginTask() ──► 0D/13/0B ──► endTask()
                                        │                        │
                                        └─ 取消计时              └─ 前台：保活 60s
                                                                   后台：立即 close()
```

**为什么是 30 s / 60 s**：30 s 够用户「打开 App → 走到门口 → 点开锁」；60 s 够「开完门 → 想起来还要读个电量 / 再开一次」。再长就是白白占着锁的并发名额（实测并发建连会从 ≤2 s 劣化到 6.8 s，见 8）。

### 4.3 任务计数

`inFlight` 是 `synchronized(flightLock)` 保护的计数器，不是布尔 —— 因为可能同时有「开锁 + 读电量」两个协程。

规则：**只要 `inFlight > 0`，后台化就不掐连接**。计数器归零才走退让逻辑。

⚠️ 这三件套必须成对调用。`unlock()` / `battery()` 都用 `try/finally { endTask() }` 包住，异常路径也要减。

### 4.4 收发内核

**发**（`sendFrame`）：

```
frame_hex → splitPackets(20B) → 逐包 writeCharacteristic
                                  │
                                  ├─ 等 onCharacteristicWrite 回调（1.5 s 超时）
                                  ├─ 失败重试 4 次（间隔 60 ms）
                                  └─ 包间隔 20 ms（**末包不等**）
```

**收**（`recvFrame`）：

```
循环（4 ms 轮询）：
  取 rxBuf → expectedFrameLength()
      ├─ 够长 → 截断出整帧，多余字节留缓冲，返回
      └─ 不够 → 距上次收到 ≥60 ms 就当作完整帧兜底返回
```

两个关键点：
1. **4 ms 轮询**而不是阻塞等待 —— 通知回调只往 buffer 里塞，判定在协程里做。
2. **多余字节留缓冲** —— 一包可能同时带来上一帧尾巴和下一帧开头。

**重连复用**（`ensureConnected`）：

```kotlin
if (address != null && isReady && currentAddress == address) return  // 跳过建连 + 服务发现
```

建连 + 服务发现是 1~7 s，是最贵的一段。所以复用判断必须放在 `connect()` 之前。

### 4.5 并发模型

| 机制 | 作用 |
|---|---|
| `connectMutex` | 同一时刻只有一条建连在跑；后来者**排队等它跑完**，不取消 |
| `rxLock` | 保护 `rxBuf`，通知线程与协程线程共享 |
| `flightLock` | 保护 `inFlight` |
| `writeSignal` | `CompletableDeferred`，把 GATT 写回调桥接成挂起点 |

⚠️ **不要 `cancelAndJoin` 预热协程**。曾经为了「让用户点开锁立刻响应」去取消预热再重连，结果每次都是从零开始建连（1~7 s），反而更慢。正确做法是让 mutex 排队，等预热跑完直接复用 GATT。

⚠️ **协程取消时必须 `close()`**：`ensureConnected` 的 `catch (CancellationException)` 里关连接，否则 GATT 悬挂，下次连接直接超时。

### 4.6 ViewModel 与 UI

`UnlockViewModel` 持有 `bleClient` 与 `credentialStore`，暴露：

| 属性 | 类型 | 说明 |
|---|---|---|
| `state` | `Idle / Success / Failure` | 单次操作结果（不是连接状态） |
| `locks` | `List<LockCredential>` | 已添加凭证 |
| `logText` | `String` | 本次操作时序日志 |
| `batteryText` | `String?` | 电量显示 |

连接状态**不走 ViewModel**，由 `MainScreen` 的 `ConnectionBadge` 每 500 ms 直接轮询 `bleClient.isReady / isConnecting / readyAddress` —— 连接是进程级状态，塞进 ViewModel 会出现两个真相。

`prewarm()` 的目标固定是 `CredentialStore.primary()`（列表第一把），**直连不发扫描**。多锁场景下只预热一把，不并发。

### 4.7 凭证存储

```
LockCredential(name, address, activationKey, serviceUuid, deviceName)
        │
        ▼
CredentialStore
  ├─ Android Keystore 生成/取回 master key（AES-256）
  ├─ AES/GCM/NoPadding 加密整份 JSON
  └─ SharedPreferences 存 {ciphertext_b64, iv_b64}
```

要点：
- **一个 GCM IV 只能用在一次加密**。每次 `persist()` 都用新 IV，和密文一起存。
- Keystore alias 丢了（卸载/清数据）→ 密文永久解不开，只能重新抓凭证。`load()` 解不开时返回空列表而不是崩。
- `maskedKey()` 只露头尾各 4 位，UI 任何地方都不显示完整 key。
- `allowBackup=false` + `data-extraction-rules.xml` 里 **`exclude` 整个 `sharedpref` 与 `file` 域**（云备份、设备迁移两条都排）—— key 不能跟着备份走。

### 4.8 抓取链路（Shizuku）

目标：拿到天猫校园打印在 logcat 里的 `activationKey`。难点在于 Android 4.1+ 普通 App 只能读自己进程的日志，`READ_LOGS` 是系统级权限。

```
GrabForegroundService（本 App，dataSync 前台服务）
        │  绑定
        ▼
ShizukuLogcatGrabber  ──bindUserService()──►  LogcatGrabService（跑在 shell 进程）
                                                    │ ProcessBuilder("/system/bin/logcat", ...)
                                                    ▼
                                              LogcatParser  → LockCredential
```

三个必须记住的坑（都真机复现过）：

1. **`Shizuku.newProcess` 在 13.1.5 已是 private**。必须用 `UserService`（AIDL + `bindUserService()`），在 shell 进程里自己 `ProcessBuilder` 起 logcat。manifest 无需声明 service。
2. **UserService 进程不随 unbind 消亡**。它会 reparent 到 init，root 下一直挂着 `com.fktm.app:grab`。覆盖安装后旧进程跑旧代码 → `onServiceConnected` 永不回调（表现：点抓取一直转圈）。
   → 修复：`version` 用 **APK `lastUpdateTime`（秒）**；`grab()` 开头扫 `/proc/<pid>/cmdline` 自我回收同名僵尸。
3. **必须起前台服务**。用户点「开始抓取」后会切到天猫校园去开锁，此时 fkTM 在后台，ColorOS 会直接冻结进程（`OplusHansManager: freeze uid`），协程和 binder 回调全停。
   → 必须**在 App 仍在前台时**启动 `GrabForegroundService`（Android 12+ 禁止后台启动前台服务）。「开小窗」也救不了。

另一个细节：logcat 的 tag 是**精确匹配**。`VocLockHelper` 匹配不到 `VocLockHelperV2`，必须写 `VocLockHelperV2:V`。

### 4.9 三个入口

| 入口 | 实现 | 说明 |
|---|---|---|
| 主界面按钮 | `MainActivity` + Compose | 常规路径 |
| 下拉快捷磁贴 | `QuickUnlockTileService` | `BIND_QUICK_SETTINGS_TILE` |
| 桌面快捷方式 | `intent-filter: fktm://unlock` | `res/xml/shortcuts.xml` |

三条都汇到 `UnlockViewModel.unlock()`。磁贴和快捷方式可能在 App 未前台时触发 —— 这时 `setForeground(true)` 没跑过，连接是冷的，所以 `unlock()` 内部必须自己 `ensureConnected()`，不能假设预热已成功。

### 4.10 性能预算

实测（OPPO PKR110 / Android 16 / VOC 锁）：

| 场景 | 耗时 |
|---|---|
| 无预热，冷连接全流程 | ~3000 ms |
| 预热后首次开锁 | **1230 ms** |
| 连接复用（二次开锁） | **367 ms** |
| 建连 + 服务发现（最贵一段） | 1~7 s |

分阶段日志（`unlock()` 的 `finally` 里打）：

```
阶段耗时  0D=xx ms  13=xx ms  0B=xx ms  协议合计=xx ms
```

`协议合计` 远小于墙钟时间 = 瓶颈在连接，不在协议；反之就是帧长判定或包间隔的问题。

---

## 5. 构建、测试与调试

### 5.1 环境

| 组件 | 版本 / 路径 |
|---|---|
| JDK | 21（`C:/Program Files/Java/jdk-21`） |
| Android SDK | `C:/Users/64702/AppData/Local/Android/Sdk` |
| Gradle | wrapper 8.11.1 |
| AGP / Kotlin | 8.9.2 / 2.0.21 |
| compileSdk / minSdk | 36 / 26 |
| Python | 3.13（pycryptodome、bleak、PIL） |

⚠️ `build.gradle.kts` 里的**阿里云 maven 镜像必须保留**，删了国内构建会卡死。

### 5.2 命令

```bash
# Android：单测 + 构建 debug
cd fktm-android
export JAVA_HOME="C:/Program Files/Java/jdk-21"
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Python：协议自检（不需要门锁）
python fktm/cli.py selftest

# 离线仿真
python fktm/cli.py simulate

# 真机（PC 蓝牙）
python fktm/cli.py scan
python fktm/cli.py unlock
python fktm/cli.py battery
python fktm/cli.py hold --seconds 300
```

**两条基线**：Python `54/54`、Android `61/61`。改协议层两边都要跑。

### 5.3 日志与排障

```bash
adb logcat -s fkTM:D          # App 业务日志（含 BleLockClient 的时序）
adb logcat -s fkTMBle:D       # 仅 BLE 层
```

`BleLockClient.note()` 每条都带相对时间戳 `t=xxx.xxxs`，`clearLog()` 在每次操作前重置基准 —— UI 上只显示本次。

常见失败 → 定位：

| 现象 | 大概率原因 |
|---|---|
| `未发现门锁 <MAC>` | 锁没电 / 超范围 / 已被别的手机连着（VOC 只有 1~2 个并发名额） |
| `建立加密链路失败：activationKey 不匹配` | key 不属于这把锁；或 `13` 请求被写成了密文帧 |
| 开锁无响应但 13 成功 | 末包 sleep 没去掉 / 包间隔太短被锁丢包 |
| `GATT 写入失败` | 连接被抢；需要退到后台让出再重连 |
| 抓取一直转圈 | UserService 僵尸进程（见 4.8 坑 2） |
| 抓取成功但切后台就失败 | 没起前台服务（坑 3） |
| `No Bluetooth adapter found`（PC） | 重启恢复，别改代码 |

---

## 6. 发布流水线

### 6.1 两阶段

```
workflow_dispatch(prepare-release.yml)
  改版本号 → 写 changelog → 更新 stable.json(isReady=false) → 推 main → 触发下一阶段
        │
        ▼
workflow_dispatch(release.yml)
  校验清单与 gradle 版本一致 → 建 tag → 签名构建 → 发 Release
  → 回写 stable.json(isReady=true) → 推 main
  → landing job（uses: deploy-pages.yml）部署落地页
```

### 6.2 清单是唯一版本源

`update/stable.json` 是**唯一的版本真相**，`app/build.gradle.kts` 必须对齐 —— `release.yml` 第一步就校验，不一致直接 fail。

字段：`channel / versionName / versionCode / isReady / releaseNotes / releaseUrl / assets[]`（含 `sha256`、`size`）。

⚠️ 这是**并发写热点**（两个 workflow 都会推它）。动它之前先 `git fetch`，push 一律走：

```bash
.github/scripts/push-with-retry.sh main      # fetch → rebase → 重推，5 次
```

`fetch/push` 遇到 `schannel handshake failed` 是常态，脚本已经带重试。

### 6.3 签名与升级连续性

- 证书 SHA-256 钉死在 `EXPECTED_CERT_SHA256`，构建后校验，不匹配就 fail。
- **换 keystore = 老用户无法覆盖安装**（只能卸载重装，凭证全丢）。这条比发版失败严重得多。
- Release 资产名必须是 `TMKey-{versionName}.apk`。`gh release create` 默认用本地 basename，所以脚本先 `cp` 成目标名再传，传完回读 `gh release view --json assets` 校验。

### 6.4 落地页部署

唯一实现 = `deploy-pages.yml`（`on: workflow_call`）：

```
cp update/stable.json docs/stable.json
cp fktm/p0_capture.bat docs/p0_capture.bat
→ configure-pages → upload-artifact(docs) → deploy-pages
```

两个入口：`pages.yml`（push 命中 `docs/**` 等路径 / 手动）、`release.yml` 的 `landing` job（发版后）。

⚠️ **别用 `workflow_run` 做接力** —— v1.0.4 发版成功后 GitHub 根本没创建 workflow_run，白等。改用 `uses:` 显式调用才可靠。

`docs/stable.json` 和 `docs/p0_capture.bat` 都是**构建产物**，已进 `.gitignore`，源头在 `update/` 和 `fktm/`。

---

## 7. 扩展指南

### 7.1 加一条新命令

以加「查固件版本」（假设 cmd = `0F`）为例，**两边同步改**：

```
1. fktm/voc_protocol.py
   - CMD_XXX = "0F"
   - 若请求是明文固定帧：加进 FIXED_FRAMES 和 CMD_IS_CLEAR_FRAME（True）
   - 若走密文：不用加，用 build() 现场生成
2. fktm/cli.py  selftest 里补黄金向量用例
3. fktm/mock_lock.py  仿真里实现该命令的响应
4. VocProtocol.kt  常量 + frameXxx() 方法
5. BleLockClient.kt  加 public suspend fun
6. UnlockViewModel + MainScreen 接按钮
7. 跑 selftest(54+N) 和 testDebugUnitTest(61+M)
```

⚠️ 先用 `mock_lock` 跑通再上真机。真机上一条错误命令可能触发锁体保护（`0c` 系统锁定）。

### 7.2 换锁型号 / 固件升级

按这个顺序验，**别跳**：

1. **抓一帧原始日志**：确认广播名、Service UUID、写/通知特征 UUID 是否变了。
2. **确认命令帧型**：`0D` 是否仍是明文固定帧？`13` 请求是否还是明文？（这两条最容易变）
3. **确认帧长**：用 `expected_frame_length()` 打一遍，看实际收到的帧是否匹配。不匹配就回退 60 ms 空闲兜底。
4. **确认成功判定**：跑一次开锁，看返回是裸 ack 还是密文。
5. **更新自检黄金向量**：把新抓的帧加进 `cli.py` 的 golden vectors，锁死行为。

只有第 5 步做完，这次适配才算固化 —— 否则下次固件再变，又得从头猜。

### 7.3 移植到其他平台

协议层是纯函数，照这个清单搬：

```
□ hex 工具（大小写统一！）
□ 校验码 computeValidateCode
□ xor（只取末 16 字节）
□ AES-128-ECB-NoPadding（注意：明文必须补到 16 的整数倍）
□ assemble_distribute_content（字面量 FF 前缀 + 补 0 对齐 32 hex）
□ build / parse / extract_message / extract_data
□ frameRequestRandomNumber / frameEncryptedLinkEstablish / frameUnlock / frameBattery
□ expected_frame_length（两种帧型的分支）
□ 用 54 项自检的向量逐条对齐
```

iOS 上对应 CoreBluetooth，注意 `CBCharacteristicProperties` 的位掩码语义与 Android 不同（`.write` vs `.writeWithoutResponse`），挑特征时两个都要认。

### 7.4 多锁

当前设计：`CredentialStore` 存列表，`primary()` = 列表第一把（最近添加）。预连接只连这一把。

要支持「按位置自动选锁」，最小改动：

```
扫描拿到 [{address, rssi}] → 与凭证列表求交集 → 取 RSSI 最强
```

但注意：**扫描会慢 1~2 s**，而且楼里可能有同名锁。如果要做自动选锁，建议只用 RSSI 给 UI 排序建议，**开锁仍然点击指定的那一把**，不做自动决策。

---

## 8. 设计取舍

| 决策 | 原因 | 代价 |
|---|---|---|
| 不做长连接 | 并发建连从 ≤2 s 劣化到 **6.8 s**（实测 346 → 7618 ms）。锁的并发名额有限，占着会拖慢所有人 | 每次冷启要建连 1~2 s |
| 退到后台**立即**让出 | 同上，息屏也走 onStop，天然兜底 | 切个 App 回来要重连 |
| `13` 用明文固定帧 | 真机验证；密文帧会被锁直接丢弃 | 与反编译常量表的直觉冲突 |
| 帧长判定而非固定 200 ms 等待 | 单帧节省 ~140 ms × 3 条命令 | 帧型判断错就退化成兜底路径 |
| 不 `requestMtu(64)` | 20 B 分片已验证稳定，帧长都很短（≤24 B），协商 MTU 收益小、兼容风险大 | 大帧时多几个包 |
| 协议层零 Android 依赖 | 能在 JVM 单测 + PC 上跑全量自检，不需要真机 | 需要手工保持两套实现同步 |
| 成功判定双路径 | 不同固件返回不同 | 判定逻辑略啰嗦 |
| 日志不打 `actualAesKey` | 它与 randomNumber 同时出现即可反解开锁钥匙 | 排障时少一个字段（但可用 `random XOR key` 反推） |
| 凭证用 Keystore 加密 | key 泄露 = 钥匙泄露，且无法吊销 | 卸载重装后无法恢复，必须重抓 |

---

## 9. 安全边界

**这套代码的合法用途只有一个：开自己有权限开的那把锁。**

红线：

- ⛔ 不添加他人门锁、不尝试开他人门锁。
- ⛔ `activationKey` 等同物理钥匙：不写入代码、不提交仓库、不上传云端、不贴到聊天/截图。
- ⛔ 日志与崩溃上报**不得包含** `activationKey` 与 `actualAesKey`。
- ⛔ 不实现「批量扫描 + 尝试开锁」这类功能，即使技术上可行。
- ⛔ `0C`（重置）不接 UI —— 它会作废对方的 activationKey。

仓库自身的防护：`.gitignore` 排除 `fktm/credentials.json`；`CredentialStore` 用 Keystore + GCM；`android:allowBackup="false"`；Release 签名证书钉死。

---

## 附：关键文件索引

| 想知道什么 | 看哪里 |
|---|---|
| 协议常量、加解密、组帧 | `fktm/voc_protocol.py` |
| 真机黄金向量 | `fktm/p0_capture.txt`、`fktm/p2_real_capture.txt` |
| 自检用例怎么写 | `fktm/cli.py` 的 `_selftest_protocol` / `_selftest_golden_vectors` |
| 门锁仿真 | `fktm/mock_lock.py` |
| 连接生命周期 / 保活 | `fktm-android/.../ble/BleLockClient.kt`（对照 `setForeground` / `beginTask` / `endTask` / `scheduleIdleClose`） |
| 收发内核 | 同文件 `sendFrame` / `recvFrame` / `ensureConnected` |
| 凭证加密 | `.../credential/CredentialStore.kt` |
| 抓取链路 | `.../credential/ShizukuLogcatGrabber.kt` + `LogcatGrabService.kt` |
| 更新清单规范 | `update/README.md` |
| 发布流水线 | `.github/workflows/release.yml` + `prepare-release.yml` |
