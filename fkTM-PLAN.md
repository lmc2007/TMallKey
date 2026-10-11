# fkTM — 天猫校园宿舍门禁快速开锁 实施方案

> 目标：一个叫 **fkTM** 的软件，一点即开，替代天猫校园 App 里那条「打开 App → 找服务 → 进 H5 → 点开锁」的漫长路径。  
> 侦查基线：`天猫校园.apk` (117 MB, 15 dex)，静态分析已完成，协议 100% 还原，无猜测项。  
> **进度（2026-10-11）**：P0 取证 ✅ → P1 协议复刻 ✅（真机逐字节验收）→ P2 客户端代码就绪 🟡（待有蓝牙的环境实机验证）→ P3 App 未开始。

---

## 0. 一句话结论

宿舍门禁是 **BLE 蓝牙智能锁（VOC 品牌）**，开锁**不经过任何网络请求**。  
整条链只需要 `macAddress` + `activationKey` 两个参数，走的是一套 `55AA` 帧 + `AES-128/ECB` 的自定义 BLE 协议。  
**只要把 `activationKey` 抓出来一次，就能完全离线、独立开锁** —— 这就是 fkTM 能做到 1~2 秒秒开的原因。

因此：**方案 1（hook 拉起天猫校园）应当放弃；方案 2（协议复刻）是正确的路，而且是本项目的最优解。**

> **✅ 已实测验证**：`activationKey` 已抓到（存于 `fktm/credentials.json`），协议复刻与真机抓包**逐字节一致**。
> 方案 2 在「协议正确性」这一层已经**没有未知项**，剩下的只是工程实现。

---

## 1. 侦查结果（已实证，可复核）

### 1.1 调用链

```
H5 页面 (pages.tmall.com / h5.m.taobao.com 上的门禁页)
   │  JS Bridge 调用
   ▼
VocLockBridge / ZMUnlockJsBridge          com.tmall.campus.bizwebview.plugin
   │  入参 JSBParams{ macAddress, activationKey, advertisString }
   ▼
VocLockHelper (BLE 状态机)                 com.tmall.campus.doorlock
   │  工作流: 请求随机数 → 建立加密链路 → 开锁 → 结束
   ▼
BluetoothHelper (协议编解码 + 加解密)       com.tmall.campus.doorlock.bluetooth
   │  55AA 帧 / AES-128-ECB / XOR 派生密钥
   ▼
Android BluetoothGatt  ──BLE──▶  门锁 (~vTmallLock)
```

> 另外存在一套旧的通用 SDK `com.mvlock.supriseble`（MarvelLock），是备选/历史实现。**当前门禁走的是 `com.tmall.campus.doorlock` 这套。** 实施时以它为准。

- 关键类所在 dex：`VocLockHelper` / `VocLockBridge` / `JSBParams` → **classes15.dex、classes14.dex**
- `BluetoothHelper`（协议核心）→ **classes15.dex**
- `AESUtils` → **classes3.dex**

### 1.2 BLE 协议格式

**广播发现**

- 设备广播名：`~vTmallLock`
- 门锁 MAC 藏在广播数据里：以 `FF` 标记起算，取后 12 个 hex，**字节逆序**才是真 MAC（`formatMacAddress` 会反转）
- `advertisString` 第 `[10:28]` 位 = 门锁序列号

**GATT 特征**

| 用途      | UUID                                              |
| ------- | ------------------------------------------------- |
| Service | `00002760-08c2-11e1-9073-0e8ac72e1001`（可被服务端配置覆盖） |
| Write   | 服务内第一个可写特征                                        |
| Notify  | 服务内第一个支持 notify 的特征                               |
| CCCD    | `00002902-0000-1000-8000-00805f9b34fb`            |

**帧结构**

```
55 AA | sign(1B) | len(2B, 大端) | payload | checksum
                                  checksum = (前面所有字节之和) % 256
```

分包：每包 20 字节一写，包间 delay 50ms。

**命令码（sign → 语义）**

| sign | 命令                              | 固定帧              |
| ---- | ------------------------------- | ---------------- |
| `0D` | 请求随机数 REQUEST_RANDOM_NUMBER     | `55AAFF0D00000B` |
| `13` | 建立加密链路 ENCRYPTED_LINK_ESTABLISH | `55AAFF13000011` |
| `0B` | **开锁 UNLOCK**                   | 动态构建（明文为空）       |
| `01` | 电量 BATTERY_LEVEL                | `55AAFF010000FF` |
| `0C` | 重置 RESET                        | `55AAFF0C00000A` |
| `06` | 激活 ACTIVATE                     | 动态构建             |

**开锁工作流**（`VocStatistics.UNLOCK_WORK_FLOW`）

```
REQUEST_RANDOM_NUMBER → ENCRYPTED_LINK_ESTABLISH → UNLOCK → UNLOCK_FINISH
```

成功判定：响应解密后以 `ff` 开头，且 `[8:10]` 状态码 == `00`；  
或裸 ack 前缀命中 `55AAFF0B0001000A`。

### 1.3 加密方案

**密钥派生**（challenge-response，核心）

```
randomNumberHex = 从 0D 响应里取 [14:46]（32 hex = 16 字节），响应须匹配
                  正则 ^55AA[0-9A-F]{2}0D0011[0-9A-F]{2}[0-9A-F]{32}[0-9A-F]{2}$

actualAesKey = randomNumber(16B)  XOR  (aesKey 的最后 32 个 hex = 最后的 16 字节)
```

**加密算法**

```
算法: AES/ECB/NoPadding, 128-bit
明文: assembleDistributeContent(sign, content) —— 已补 '0' 对齐到 32 hex 的整数倍
```

`assembleDistributeContent` 结构：

```
FF | sign | contentLen(2B) | content | checksum(55AA+本段)   → 再补 '0' 对齐 32
```

最终帧 = `55AA` + `len(2B)` + `AES_encrypt(上面那段)` + `checksum`，整体大写 hex。

**两个 Key**

| 名称                | 值                                                              | 用途                 |
| ----------------- | -------------------------------------------------------------- | ------------------ |
| `DEFAULT_AES_KEY` | `564F435F426C65536D6172744C6F636B`（= ASCII `VOC_BleSmartLock`） | 激活/生成密钥阶段用         |
| `activationKey`   | **每把锁一个，由 H5 从后端下发**                                           | 正常开锁阶段用 ← **重点目标** |

### 1.4 关键常量速查

```
PROTOCOL_HEADER     = "55AA"
DEFAULT_AES_KEY     = "564F435F426C65536D6172744C6F636B"   // VOC_BleSmartLock
UNLOCK_SUCCESS_ACK  = "55AAFF0B0001000A"
VOC_LOCK_TAG        = "~vTmallLock"
CCCD_UUID           = "00002902-0000-1000-8000-00805f9b34fb"
DEFAULT_SERVICE_UUID= "00002760-08c2-11e1-9073-0e8ac72e1001"
DEFAULT_MTU         = 23
超时: SCAN=10s, CONNECT=10s, COMMUNICATION=10s, SESSION=35~50s
```

### 1.5 开锁完整时序

```
fkTM                          门锁(~vTmallLock)
 │  BLE Scan (按名字过滤)
 │◀──────────── 广播 (含真 MAC)
 │  connectGatt(MAC)
 │  discoverServices → 找 write/notify 特征 → 开 notify (写 CCCD)
 │
 │  write  55AAFF0D00000B                          (请求随机数)
 │◀──────── 55AA..0D0011..[16B random]..cksum
 │
 │  key = random16B XOR activationKey最后16B
 │  write  AES(FF 13 0000 cksum 补零)              (建立加密链路)
 │◀──────── AES 密文 → 解密校验 ff..00..           (链路 OK)
 │
 │  write  AES(FF 0B 0000 cksum 补零)              (开锁，无明文!)
 │◀──────── AES 密文 → 解密校验 ff..00..           (成功)
 │
 │  disconnect
 ▼  锁开了
```

---

## 2. 方案对比

|     | 方案 1：Hook 拉起天猫校园                                 | 方案 2：协议复刻（独立 App）         |
| --- | ------------------------------------------------ | ------------------------- |
| 原理  | Frida/Xposed 注入天猫校园，直接调 `VocLockHelper.unlock()` | fkTM 自己实现 BLE 协议 + 加解密    |
| 依赖  | **必须**天猫校园已安装/能启动                                | 无，完全独立                    |
| 速度  | 冷启动 3~~8s；热进程 1~~3s（还得保活）                        | 扫描+连接+3 帧 ≈ **1~2s**，可再优化 |
| 稳定性 | 受天猫 App 版本/加固/保活策略影响，随时失效                        | 协议稳定，长期可用                 |
| 成本  | 低（一次 hook 脚本）                                    | 中（需复刻协议，但已全部还原）           |
| 结论  | 仅适合做 **P0 取证**（抓 activationKey），不适合做产品           | **主线方案**                  |

**推荐：方案 2 为主，方案 1 只用作「抓一次凭证」的工具。**

---

## 3. fkTM 推荐架构

**形态：Android 原生 App**（门禁是 BLE，iOS 侧限制多、且学校设备是 Android 生态，先做 Android）

```
┌─────────────────────────────────────────────┐
│  fkTM (Android)                             │
├─────────────────────────────────────────────┤
│  触发层  桌面快捷方式 / 快捷设置磁贴 / 通知栏 / 小组件  │
│          指纹/长按 → 直接开锁                 │
├─────────────────────────────────────────────┤
│  凭证层  LockProfile{ mac, activationKey,    │
│                       advertisString, name } │
│          ← 一次性提取后本地加密存储（EncryptedSharedPreferences）│
├─────────────────────────────────────────────┤
│  协议层  VodProtocol: build() / parse() / xor() / crc()  │
│          AES-128-ECB/NoPadding               │
├─────────────────────────────────────────────┤
│  BLE 层  ／原生 BluetoothGatt（不用第三方库，省体积）│
│          直连已知 MAC（跳过扫描，省 1~2s）    │
└─────────────────────────────────────────────┘
```

**提速设计（关键卖点）**

1. **跳过扫描**：凭证里存了 MAC，直接 `getRemoteDevice(mac).connectGatt()`，省掉 1~2s 扫描。
2. **多锁配置**：宿舍门 / 楼栋门 各自存一份凭证，一键切换或按最近使用。
3. **触发即连**：点击 → 立即连接，不做任何 UI 渲染阻塞。
4. **可选预连接**：进入已知区域（地理围栏 / 连上宿舍 WiFi）后台预热 GATT 连接。

---

## 4. 分阶段计划

### P0 · 取证与真机验证 —— ✅ 已完成（2026-10-11 现场）

目的：拿到**真实的 activationKey**，并用真实抓包验证协议复刻正确。**已全部达成。**

**结果（`fktm/p0_capture.txt`，6 TX / 6 RX 帧）：**

| 项 | 值 |
|---|---|
| 门锁名称 | `~vTmallLock` |
| BLE 地址 | 实测已记录在本机凭证文件中（不入库） |
| **activationKey** | 已反解（存于 `fktm/credentials.json`，勿外传） |
| 会话 1 | 0D → 13 → **0B 开锁成功** |
| 会话 2 | 0D → 13 → 01 电量（返回 100%） |

> **关键：activationKey 甚至不需要被打印出来也能拿到。**
> 密钥派生 `actualAesKey = randomNumber XOR activationKey` 是**可逆**的，
> 而日志里同时有 randomNumber（0D 响应明文携带）与 actualAesKey（App 打印），
> 于是 `activationKey = randomNumber XOR actualAesKey`。
> 本次两组**独立会话**（不同随机数）解出**完全相同的值**（巧合概率 2⁻¹²⁸），
> 这既是取值也是对该派生逻辑的密码学级证明。
> 已固化为 `cli.py verify` 的自动反解逻辑 + `selftest` 的黄金向量。

**真机校正（推翻了两处离线推断，已修）：**

| 命令 | 离线推断 | 真机实测 | 修正 |
|---|---|---|---|
| `13` 建立加密链路 | 密文帧 | **明文固定帧** `55AAFF13000011`（只有其**响应**是密文） | `frame_encrypted_link_establish()` 改为返回固定帧 |
| `01` 电量 | 明文 `55AAFF010000FF` | **密文帧**（走 `build()`） | 新增 `frame_battery(random, key)` |

**验收证据**：`python cli.py verify --log p0_capture.txt` → **结论: 协议复刻与真机逐字节一致**
（0B 开锁帧、01 电量帧与真机**完全一致**；13/0B/01 下行帧解密结果与 App 打印逐一吻合）。

**路线 A（最终采用，无需 root）：adb logcat 直接读**
`VocLockHelperV2.unlock()` 每次开锁都会把 `macAddress` + `activationKey` 明文打进 logcat；
`VocLockHelperV2$createGattCallback$1` 的 `onCharacteristicWrite` / `onCharacteristicChanged`
还会把**每一个收发字节**打出来。全程零 root、零 hook。

1. 手机开 USB 调试，运行 `fktm/p0_capture.bat`（自动 `logcat -c` 清缓存后开始抓）。
2. 现场在天猫校园里点**一次**开锁；门开后在窗口按 `Ctrl+C`。
3. `python fktm/cli.py verify --log p0_capture.txt` → 「协议复刻与真机逐字节一致」。

> **踩坑记录（v1 → v2）**：真机走的是 `VocLockHelperV2`，其 TAG 精确为 `VocLockHelperV2`。
> 首版脚本的 logcat 过滤清单写的是 `VocLockHelper`（v1 的 TAG），**tag 是精确匹配、不会互相包含**，
> 结果只捡到了 `BluetoothHelper` 的派生密钥残留，activationKey 与收发帧全部丢失。
> v2 已把 `VocLockHelperV2` 及一组相关 tag 全量加入过滤清单。
>
> **踩坑记录（v2 → v3）**：v2 在 `.bat` 里写了一行**中文注释**。cmd 用控制台代码页（GBK/936）解析
> UTF-8 字节时解码错乱，把后续命令行**劈成碎片**当命令执行 → `set OUT=...` 失效 → `%OUT%` 为空、
> 日志写不进文件、verify 拿不到 `--log`。对照实验已排除 LF 行尾、`^` 续行、`if()` 块三种嫌疑。
> v3 修复：**脚本全文纯 ASCII**（non-ascii=0）+ **强制 CRLF**（bare-LF=0）+ 去 `^` 续行 + 去 `if` 块 +
> `call adb` + 开头打印输出路径。已用桩 adb + `make_synthetic_log.py` 本地跑通全流程。
>
> **经验（重要）**：日志里**没打印 activationKey 不等于拿不到** —— 只要同时有
> 「随机数」和「派生 key」就能反解。以后抓包优先看这两样。

### P1 · 协议复刻（离线，无需设备）—— ✅ 已完成并真机验收

1. ✅ 用 Python 实现 `build()` / `parse()` / `xor()` / `assembleDistributeContent()` / `computeValidateCode()`。
2. ✅ 自检 **41/41** 通过：固定帧与硬编码常量逐字节一致、AES-128-ECB 命中 NIST FIPS-197 向量、
   加解密往返、切包、MAC 逆序、错误码映射，**外加第 9 节「真机抓包黄金向量」**（8 项，逐字节锁定真机数据）。
3. ✅ 附带**离线门锁模拟器**，端到端跑通「请求随机数 → 建链 → 开锁」，开锁+错误密钥+错误状态码三条分支均验证。
4. ✅ 已用 `verify --log p0_capture.txt` 完成与真机抓包的**逐字节对照** → 全等。

交付物：`fktm/voc_protocol.py`、`fktm/mock_lock.py`、`fktm/logparse.py`、`fktm/cli.py`、`fktm/credentials.json`
运行：

```bash
PY=C:/Users/64702/.workbuddy/binaries/python/envs/default/Scripts/python.exe
$PY fktm/cli.py selftest                              # 41/41（含真机黄金向量）
$PY fktm/cli.py verify --log fktm/p0_capture.txt      # 真机逐字节验收
$PY fktm/cli.py frames --key <activationKey> --random <32hex>
$PY fktm/cli.py simulate --key <activationKey>
```

### P2 · BLE 客户端原型 —— ✅ 已完成（2026-10-11 真机实开成功）

- ✅ `fktm/ble_client.py`（bleak 3.0.2）：扫描 `~vTmallLock` → 连接 → 按 `SERVICE_UUID`
  自动挑 write/notify 特征 → `start_notify`（自动写 CCCD）→ 20 字节切包 @50ms →
  200ms 空闲判定消息边界 → 执行 0D/13/0B 状态机。与 `SimulatedLink` **同构**。
- ✅ CLI：`cli.py scan` / `unlock`（含 `--dry-run`）/ `battery`；凭证从 `credentials.json` 读。
- ✅ **真机实测（Windows 笔记本 + 板载 BLE，设备地址见本机凭证文件）**：

| 步骤 | 结果 |
|---|---|
| 扫描 | `~vTmallLock` 命中 |
| 服务/特征 | Service `…2760…1001`；write=`…0001`、notify=`…0002`（**UUID 不固定，按 properties 挑**） |
| 电量查询 | `[OK] 电量 100%`（content=`0064ff00`） |
| **开锁** | **`[OK] 开锁成功` —— 门实际打开** |
| 逐字节复核 | 0B 帧 == 真机 TX、派生 key == App 打印、ack 状态码全 00 |

- 📌 **方案 2 至此彻底成立**：不依赖天猫校园 App、不依赖网络的独立开锁链路已跑通。
- 证物：`fktm/p2_real_capture.txt`（完整收发时序）；该会话已固化为自检第 9 节**第三组黄金向量**，
  自检 41 → **46/46**。
- ⏱ 性能观察（P4 依据）：协议交互本身仅 **~0.9s**，瓶颈在 BLE **连接建立 4.9~7.9s**
  → P3/P4 需用「已知地址直连 + 连接缓存 + 预连接」压缩这一段。

### P3 · fkTM App（Android）—— ✅ 第一版已编译通过（2026-10-11）

工程：`fktm-android/`（Kotlin + Compose，AGP 8.9.2 / Kotlin 2.0.21 / compileSdk 36 / minSdk 26）

| 需求 | 落地 |
|---|---|
| 凭证加密存储 | `credential/CredentialStore.kt` —— **Android Keystore AES-256-GCM**，密钥不可导出；已通过 `data_extraction_rules` 排除备份/设备迁移 |
| 首次导入 | `ui/MainScreen.kt` 导入面板：支持**直接粘贴 PC 端 `credentials.json`**，或手工填地址+key |
| 原生 GATT 状态机 | `ble/BleLockClient.kt` —— 已知地址直连（失败回落扫描）→ 按 properties 挑 write/notify → CCCD → 20B 切包@50ms → 200ms 空闲判帧 → 0D/13/0B。含 GATT 写回调等待与重试（防 GATT_BUSY） |
| 错误码映射 | `protocol/VocProtocol.kt` 内 `CODE_MAP` 全表还原 |
| 触发入口 | QS 磁贴 `QuickUnlockTileService.kt`（API34+ 用 PendingIntent 版）+ 桌面快捷方式 `res/xml/shortcuts.xml`（`fktm://unlock`） |
| 失败兜底 | 权限/蓝牙未开/门锁未发现/密钥不匹配 均给中文可读提示，并展示完整收发时序日志 |

**协议层跨语言验证（关键）**：`protocol/VocProtocol.kt` 是 `fktm/voc_protocol.py` 的逐字节移植，
JVM 单测 `VocProtocolTest` **22/22 通过**，覆盖：

- 三组**真机黄金向量**（P0 开锁 / P0 电量 / P2 实开）
- AES-128 **NIST FIPS-197** 标准向量
- **跨语言对拍**：5 组由 Python 现场随机生成的 (随机数, activationKey) → 0B/01 帧，Kotlin 复算逐字节一致

构建：

```bash
cd fktm-android
export JAVA_HOME="C:/Program Files/Java/jdk-21"
./gradlew :app:testDebugUnitTest :app:assembleDebug
# 产物 app/build/outputs/apk/debug/app-debug.apk（≈9.7 MB）
```

**尚未做（下一步）**：真机安装与实开验证（需要把你的手机用 USB 连上，`adb install -r`）。

### P4 · 提速 —— ✅ 已实施（2026-10-11，用户实测 3000ms → 目标首次 ~2s / 二次 <500ms）

**瓶颈拆解（P2 真机时序为据）**

| 阶段 | 实测 | 性质 |
| --- | --- | --- |
| connect + 服务发现 | 4.9~7.9s（PC/bleak）；Android 约 1.5~2s | 系统层，不可避免（除非复用连接） |
| CCCD 写入 | 29ms | — |
| 0D→响应 | 136ms | 门锁真实响应 |
| **空闲 200ms 判帧 ①** | **219ms** | ⚠️ **纯等待** |
| 13→响应 | 36ms | — |
| **空闲 200ms 判帧 ②** | **240ms** | ⚠️ **纯等待** |
| 0B 两包（含 50ms 死等） | 91ms | 一半是死等 |
| **空闲 200ms 判帧 ③** | ~200ms | ⚠️ **纯等待** |
| close / disconnect | 156~356ms | 与开锁结果无关，却阻塞 UI 反馈 |

**核心洞察：每个响应的帧长度都能从帧头直接算出来，根本不必死等 200ms。**

- 明文帧 `55AA｜FF｜cmd｜len(2B)｜payload｜cksum` → 总长 = **7 + len**（0D 响应 = 24 字节）
- 密文帧 `55AA｜len(2B)｜cipher｜cksum` → 总长 = **5 + len**（13 / 0B ack = 21 字节）

**已实施的优化**

| # | 措施 | 收益 | 落地 |
| --- | --- | --- | --- |
| 1 | **帧长精确判定**（收满即返回）替代 200ms 空闲判帧 | **~600ms** | ✅ PC + Android |
| 2 | 末包不再 sleep；包间 50ms → 20ms | ~60ms | ✅ 两端 |
| 3 | `requestConnectionPriority(CONNECTION_PRIORITY_HIGH)` | 缩短每次 GATT 往返 | ✅ Android |
| 4 | **连接复用**：进程级共享 client，60s 内二次开锁跳过「连接 + 服务发现」 | **1~3s** | ✅ Android |
| 5 | 收尾异步化：`close()` 移出开锁路径，交给空闲任务 | 150~350ms | ✅ Android |
| 6 | 收帧轮询 15ms → 4ms | ~30ms | ✅ Android |

**预期效果**：首次 3000ms → **约 2.0s**；60s 内二次开锁 → **约 400ms（秒开）**。

**连接保活的安全边界**：门锁通常只允许少量并发连接。fkTM 在开锁后保活 60s，期间可复用；
超时自动断开让出，不会长期占用影响他人使用（这也是没有做「永久长连接」的原因）。

**测试基线**：Python `cli.py selftest` → **54/54**；Kotlin 单测 → **27/27**
（新增帧长判定黄金向量：10 个真机帧全部精确命中）。

**后续可选（未做，按需再评估）**
- MTU 提升（`requestMtu(64)`）让 0B 帧一包发完 —— 省 ~30ms，但需确认门锁固件接受 >20B 写，风险 > 收益
- 预连接 / 预热（App 启动即连）—— 需权衡耗电与「人不在门附近」时的无效连接
- 多锁管理（数据结构已支持）、开锁反馈（震动 / Toast / 铃声）

---

## 5. 关键待验证项

| # | 问题 | 状态 | 结论 / 怎么验证 |
| - | --- | --- | --- |
| 1 | `activationKey` 从哪来？ | ✅ 已答 | H5 传给 JSBParams；真机 logcat 里 `VocLockHelperV2.unlock()` 直接打印。**即使不打印也能反解**（见 P0） |
| 2 | activationKey 会不会轮换？ | ⏳ 待观察 | 隔几天再抓一次比对（若轮换，则 P3 要加"定期重抓"或让用户在 App 内定期走一次天猫校园） |
| 3 | `SERVICE_UUID` 是否被服务端改过？ | ✅ 已答 | 真机实测用默认值 `00002760-08c2-11e1-9073-0e8ac72e1001` |
| 4 | 门锁是否校验「已绑定设备」？ | ✅ 已答 | **不校验**。开锁凭证就是 activationKey，谁拿到谁能开 —— 真机两会话互验一致即为证 |
| 5 | 门锁是否只认已激活的 key？ | ✅ 已答 | 是；激活后的 key 才有效。绕过绑定不需要（因为不校验设备） |
| 6 | 开锁是否有服务端风控/次数限制？ | ⏳ 待观察 | fkTM 走纯 BLE、零网络请求，理论上不触发；实际高频行为需自行留意校规 |
| 7 | **两种帧布局是否属实？** | ✅ 已答 | 属实：明文帧 `55AA\|FF\|cmd\|len\|payload\|cksum`；密文帧 `55AA\|len\|cipher\|cksum`。真机 0D/13 是明文、0B/01 是密文 |
| 8 | **0D 响应第 [12:14] 那 1 字节是什么？** | ✅ 已答 | 真机两次分别是 `00`/`00`（恒定），语义仍未定但不影响派生 |
| 9 | 广播包里 MAC 的定位偏移是否恒为 `FF+4`？ | ➖ 不再必要 | 真机扫描日志直接给出 `deviceId`（BLE 地址），无需从广播里抠 MAC |
| 10 | **13 是明文还是密文帧？** | ✅ 已答 | **明文固定帧** `55AAFF13000011`（离线推断错误，已修） |
| 11 | **01 电量是明文还是密文帧？** | ✅ 已答 | **密文帧**（走 `build()`），常量表里的明文 `55AAFF010000FF` 未被使用 |
| 12 | **开锁明文里到底有没有业务数据？** | ✅ 已答 | **没有**。真机 0B 密文解回 = `FF\|0B\|0000\|09\|补零`，content 为空 |

---

## 6. 风险与边界（直说）

- **仅限本人宿舍门。** 拿别人的 activationKey 去开别人的门，是另一回事，这条线不能碰。
- **账号风险**：H5 层可能有开锁埋点上报，异常高频可能触发风控；fkTM 只走 BLE，理论上不上报，但 P0 阶段会看到真实行为。
- **学校规定**：绕开门禁 App 可能违反校规，后果自负。
- **凭证=钥匙**：activationKey 泄露 = 门锁被开。文件必须加密存储，别同步到云盘/别发群里。
- **封包不可逆**：一旦把门锁 reset（`0C`），原 activationKey 作废，需要重新激活 —— 调试时**千万别误发 0C**。

---

## 附录 A · 已还原文件索引（jadx 反编译产物）

```
tools/out2/sources/com/tmall/campus/doorlock/
    VocLockHelper.java              # BLE 状态机（工作流/超时/回调）
    VocLockHelper$unlock$1.java     # 开锁帧构建：build("0B","",random,activationKey)
    VocStatistics.java              # 全部常量 + 工作流顺序
    JSBParams.java                  # { macAddress, activationKey, advertisString }
    bluetooth/BluetoothHelper.java  # ★协议编解码 + 加解密核心
    bluetooth/VocCommandType.java   # 命令码表
    session/VocLockSession.java     # 会话状态机
tools/out2/sources/com/tmall/campus/bizwebview/plugin/
    VocLockBridge.java              # H5 桥（unlock/activate/reset/battery/disconnect）
    ZMUnlockJsBridge.java           # 另一个开锁桥
tools/out3/sources/com/tmall/campus/utils/AESUtils.java   # AES/ECB/NoPadding
tools/out2/sources/com/mvlock/supriseble/                 # 旧版 MarvelLock SDK（备选）
```

### 附录 A2 · fkTM 代码索引

```
fktm/
  voc_protocol.py     # ★协议层：与 BluetoothHelper.java 方法一一对应（帧/加解密/密钥派生/切包/重组）
  mock_lock.py        # 离线门锁模拟器 + SimulatedLink（P2 的同构骨架）
  ble_client.py       # [P2] 真实 BLE 客户端（bleak）：扫描/连接/notify/切包/0D-13-0B 状态机
  logparse.py         # P0 logcat 解析 + activationKey 反解 + 与复刻帧逐字节对照
  cli.py              # selftest / frames / simulate / verify / scan / unlock / battery
  p0_capture.bat      # P0 一键抓包（纯 ASCII + CRLF；抓完自动 verify）
  make_synthetic_log.py  # 生成真机同格式假日志，用于离线自测 verify 链路
  credentials.json    # ★凭证库（=钥匙）。勿外传、勿上云、勿入库
  p0_capture.txt      # 2026-10-11 真机抓包原始日志
  p0_capture_v1.txt   # 首次失败抓包（保留作踩坑证据）
  p2_real_capture.txt # [P2] fkTM 自身经 bleak 真开锁成功的完整时序证物

fktm-android/                    # [P3] Android App（Kotlin + Compose）
  app/src/main/java/com/fktm/app/
    protocol/VocProtocol.kt      # ★协议层：voc_protocol.py 的逐字节移植
    ble/BleLockClient.kt         # 原生 GATT 客户端（扫描/直连/切包/0D-13-0B）
    credential/CredentialStore.kt# Keystore AES-GCM 加密的凭证库
    credential/LockCredential.kt # 凭证数据模型
    ui/MainScreen.kt             # Compose 主屏（开锁大按钮/收发日志/导入面板）
    ui/theme/Theme.kt            # Material3 主题
    vm/UnlockViewModel.kt        # 开锁状态机（UI 层）
    MainActivity.kt              # 主入口 + fktm://unlock 自动开锁
    QuickUnlockTileService.kt    # QS 磁贴「一键开锁」
  app/src/test/java/com/fktm/app/protocol/VocProtocolTest.kt   # 22 项（含三组真机黄金向量 + 跨语言对拍）
```

**测试基线**：

- Python：`python cli.py selftest` → **46/46** 通过。第 9 节是**真机黄金向量**（三组会话）。
- Kotlin：`./gradlew :app:testDebugUnitTest` → **22/22** 通过（含 AES NIST 向量、三组真机黄金向量、
  以及 5 组 Python 现场随机生成数据的**跨语言逐字节对拍**）。

两侧任何改动若破坏协议兼容性，都会立即被自己的测试拦下。

## 附录 B · P0 用 Frida 取证脚本（草案）

```javascript
Java.perform(function () {
    // 1) 抓开锁凭证（最关键）
    var JSBParams = Java.use("com.tmall.campus.doorlock.JSBParams");
    JSBParams.getActivationKey.implementation = function () {
        var v = this.getActivationKey();
        console.log("[KEY] activationKey = " + v);
        return v;
    };
    JSBParams.getMacAddress.implementation = function () {
        var v = this.getMacAddress();
        console.log("[KEY] macAddress = " + v);
        return v;
    };

    // 2) 抓真实上下行帧
    var BH = Java.use("com.tmall.campus.doorlock.bluetooth.BluetoothHelper");
    BH.build.overload('java.lang.String','java.lang.String','java.lang.String','java.lang.String')
      .implementation = function (sign, content, rnd, key) {
        var out = this.build(sign, content, rnd, key);
        console.log("[TX] sign=" + sign + " rnd=" + rnd + " key=" + key + "\n     frame=" + out);
        return out;
      };
    BH.parse.overload('java.lang.String','java.lang.String','java.lang.String','java.util.regex.Pattern')
      .implementation = function (proto, rnd, key, pat) {
        var p = this.parse(proto, rnd, key, pat);
        console.log("[RX] proto=" + proto + "\n     -> " + p);
        return p;
      };
});
```


```

---
*侦查时间：2026-10-11 · 分析对象：天猫校园.apk (com.tmall.campus) · 工具：jadx 1.5.1*
```
