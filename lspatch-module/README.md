# tmxy-cookie-dump — LSPatch 模块（免 root 登录态同步）

把**天猫校园**（`com.tmall.campus.and`）已登录的 cookie 同步给「快开门」（`com.fktm.app`），
**全程免 root**。

## 实测结论（2026-10-21，OPPO PKR110 / Android 16 / KernelSU）

- LSPatch 打包的天猫校园**能正常打开和登录** —— 前提是用 **JingMatrix/LSPatch v0.8** 且带
  `-l 2`（签名伪装等级 2，把原版签名内嵌进补丁包，SDK 查签名时喂回原值）。
- 官方 LSPosed/LSPatch v0.6 **没有** `-l` 选项，打出来的包一开就崩（阿里安全 SDK 查签名）。
- 模块在天猫校园进程里读 `CookieManager` 拿到 32 条 cookie，通过**本地回环 Socket**
  交给 fkTM，`selfCheck`（`general.device.info`）通过。

## 5.0 为什么开不了门 · 5.8 是怎么改的（2026-11-27）

5.0 的补丁版已经**开不了宿舍门**，原因不在补丁、也不在 BLE：**钥匙下发方式变了**。

5.8.0 里多了一个 `com.tmall.campus.doorlock.VocSecretKeyDecryptor`。H5 传下来的
`activationKey` 不再是 32 位明文十六进制，而是服务端用 AES-GCM 封好的 Base64 密文：

```
H5 (JSBParams.activationKey = Base64 密文)
   │
   ▼  VocLockBridge.prepareParams(json, cb, flag)
   │     JSBParams parsed → VocSecretKeyDecryptor.decrypt(cipher)
   │       key = AppInfo.getSecurityExtraData("voc_lock_secret_key")   // 32 字节
   │       AES/GCM/NoPadding，前 12 字节 IV，密文长度必须 > 28
   ▼  JSBParams.copy(activationKey = 明文)
   ▼  VocLockHelperV2.unlock(Context, JSBParams, UnlockCallback)   ← 模块就挂在这里
   ▼  VocLockManager.unlock → VocLockSession(activationKey = params.activationKey)
```

5.0 没有这个解密器，拿到密文直接当钥匙喂给 BLE → 门锁校验不过 → “开锁失败”。

**好消息：模块不用改。** 解密发生在 `prepareParams` 里，`VocLockHelperV2.unlock` 收到的
`JSBParams` 已经是**明文钥匙**，所以 `Hook.kt` 里原有的
`unlock(Context, JSBParams, UnlockCallback)` 钩子在 5.8.0 / 5.8.6 上口径完全一致
（已核对两版 dex 的方法签名逐字节相同）。

### 版本对照

| | 5.0.0 | 5.8.0 / 5.8.6 |
|---|---|---|
| `VocLockHelperV2.unlock(Context, JSBParams, UnlockCallback)` | 有 | 有（签名一致） |
| `VocLockManager.unlock(...)` | 有 | 有（签名一致） |
| `VocSecretKeyDecryptor.decrypt(String)` | **无** | 有 |
| `activationKey` 形态 | 32 位明文 | Base64(AES-GCM) 密文，App 内解密 |
| 补丁版能否开门 | **否**（拿密文当钥匙） | 能（模块抓到的是解密后的明文） |

## 模块 1.1：cookie2 抓错域（2026-11-27）

**症状**：TMallKey 饮水页报「缺校园会话：导入的 cookie 里没有 .confong.cn 的 cookie2」，
但模块明明抓到了 30+ 条 cookie。

**根因**：模块 1.0 只按主机名查 CookieManager：

```kotlin
"https://share.confong.cn", "https://www.taobao.com", "https://h5api.m.taobao.com", ...
```

而 5.8 的校园 H5 搬到了 `biz.confong.cn`，那条 `cookie2` 的 **Path 是
`/app/tmall-xiaoyuan/campus-dormitory-m`**（见 WebView `cktest` 日志）。
`CookieManager.getCookie(url)` 是**按路径匹配**的，查 `https://biz.confong.cn/`
（path=`/`）不满足前缀条件 → 拿不到，只能拿到 `.taobao.com` 那条 → CM 网关判「未登录」。

PC 侧直连 mtop 复核（`fktm/water/cmcheck.py --cookie2 …`）：

| cookie2 来源 | `mtop.tmall.campus.member.app.user.get` |
|---|---|
| `.taobao.com` | **FAIL** |
| `biz.confong.cn` 宿舍页路径 | **OK** |

**修法**（`Hook.kt`）：`HOSTS` 增加带完整路径的宿舍页 URL，并给它最高优先级：

```kotlin
"https://biz.confong.cn/app/tmall-xiaoyuan/campus-dormitory-m/home",  // 必须带全路径
"https://biz.confong.cn",
"https://share.confong.cn",
...
```

`dumpCookieMap` 里另记 `dormCookie2`（来自宿舍页）优先于其它 confong 域的 `cookie2`，
并把命中来源写进 probe 日志，方便下次换域名时定位。

> 教训：**cookie 是按 URL 路径匹配的**，只给域名不够。以后天猫校园再换页面路径，
> 这个 URL 也要跟着换。

## 模块自身怎么编

```bash
cd lspatch-module
printf 'sdk.dir=D:/SRC/tmallkey/tools/android-sdk\n' > local.properties
ANDROID_HOME=D:/SRC/tmallkey/tools/android-sdk \
JAVA_HOME=D:/SRC/tmallkey/tools/jdk21/jdk-21.0.13.1+2 \
./gradlew :app:assembleRelease --offline
# → app/build/outputs/apk/release/app-release.apk  → 复制成 dist/tmxy-cookie-dump-<ver>.apk
```

**两个坑**：

1. **Gradle 发行包**：`gradle-wrapper.properties` 指向 `services.gradle.org`，国内经常
   读到一半超时（实测卡 14 分钟才失败）。从镜像下同一个包塞进 wrapper 缓存就行：

   ```bash
   curl -o /tmp/g.zip https://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip
   D=~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/
   rm -f $D/*.part $D/*.lck && cp /tmp/g.zip $D && (cd $D && unzip -q gradle-8.11.1-bin.zip && touch gradle-8.11.1-bin.zip.ok)
   ```

2. **SDK**：只需要 `platforms;android-36` + `build-tools;35.0.0`（AGP 8.9.2 的默认）。
   `sdk.dir` 写**正斜杠**，反斜杠在 properties 里要转义，很容易写错。

## 5.8 补丁包怎么出（一键）

```bash
tools/build-lspatched-apk.sh TmallCampus_5.8.0_Original.apk
# → out/tmcampus-5.8.0-lspatched.apk
```

脚本会检查 `lspatch.jar` 的 sha256、自动用仓库里的 `tools/jdk21`、按 `-l 2 --force` 打包，
再把 LSPatch 的 `xxx-439-lspatched.apk` 改回规范名。等价的手工命令：

```bash
java -Xmx4g -jar tools/lspatch-v0.8.jar TmallCampus_5.8.0_Original.apk \
  -m lspatch-module/dist/tmxy-cookie-dump-1.1.apk -l 2 --force -o out/
```

> **升级安装不丢登录态**：LSPatch 用内置 keystore 签名，同一个包重新打出来签名不变
> （`c081890c…`），所以换模块后直接 `pm install -r` 覆盖即可，数据与登录态都在。

## 跟着 GitHub 发版（release.yml）

补丁包不是手工上传的 —— `Release APK` 流程里有一个独立步骤
`Patch 天猫校园 (LSPatch + tmxy 模块)`，跟着主包一起构建、校验、上传，
资产名固定为 `tmcampus-<天猫校园版本>-lspatched.apk`
（落地页与 README 都直链 `releases/latest/download/` 下的这个名字）。

它做的事：

1. `lspatch-module` 从源码构建模块 APK（不用手工放 dist/）；
2. 用**库里那份** `tools/lspatch-v0.8.jar`（sha256 对不上就失败，防止误用官方 v0.6）；
3. 取原版天猫校园 APK → `-l 2 --force` 打包；
4. `python3 .github/scripts/verify-lspatched-apk.py` 把关（见下）；
5. 以 `tmcampus-<ver>-lspatched.apk` 挂到本次 Release，并在回读校验里确认资产真的存在。

### 校验脚本拦什么

`.github/scripts/verify-lspatched-apk.py` 检查的不是「文件在不在」，而是四类会真的坑到用户的问题：

| 检查 | 不拦会怎样 |
|---|---|
| `sigBypassLevel == 2` | 少了签名伪装，阿里安全 SDK 一查签名就闪退 |
| 内嵌 `origin.apk` == 输入原包 | LSPatch 走 nested apk，打错原包外面完全看不出来 |
| 内嵌模块 == 本次构建 | 发出去的是上一次的旧模块，表现为「改了代码没生效」 |
| 包名 / versionCode 未被改 | 用户必须卸载重装，登录态全丢 |
| 签名证书 == 钉住值 | keystore 换了 → 老用户装不上覆盖包 |

> 证书指纹 `EXPECTED_LSPATCH_CERT_SHA256` 钉在 workflow 里。它变了必须人工确认，
> 不能让流水线静默发出去 —— 和主 APK 的 `EXPECTED_CERT_SHA256` 是同一个道理。

### 天猫校园大版本更新时怎么改

三处一起改，别漏：

1. `.github/workflows/release.yml` 里 patch 步骤的 `TMALL_VERSION` / `TMALL_ORIGINAL_ASSET`
   （现在是 `5.8.6` / `tmcampus-5.8.6-original.apk`）；
2. 把新的原版 APK 传到 `tmall-source` 这个 Release 的资产里
   （**不要入库**：仓库不收录天猫校园 APK；临时也可以用 `workflow_dispatch` 的
   `tmall_original_url` 直接给个直链，验证完再把资产补上）；
3. README 的下载链接（3 处）、`docs/index.html`（chip 文案 + 4 处链接）。

改完先跑一次 `dry_run=true` 的 `Release APK`：它会完整走一遍构建 + 校验，
但不建 tag、不发版，是验证这条链路最省事的方式。

> 120 MB / 9 个 dex 的包会被 LSPatch 走 **nested apk**：原 APK 原封不动塞进
> `assets/lspatch/origin.apk`（stored），外面只剩一个 loader.dex。这是正常路径，
> 不是打包失败。产物因此 ≈ 原包大小 + 4 MB（loader + 4 个 ABI 的 liblspatch.so）。

## 5.8.6 真机实测（2026-11-27，OPPO PKR110 / Android 16）

| 项 | 结果 |
|---|---|
| 安装 | 卸载官方 5.8.6 后 `pm install` 成功（同包名，签名已换） |
| **第一次启动** | **崩一次**：`LSPAppComponentFactoryStub.<clinit>` → `NoClassDefFoundError` |
| 第二次启动 | 正常。loader 已把 `origin.apk` 解到 `cache/lspatch/origin/`，`androidx.core.app.CoreComponentFactory` 能解析了 |
| 模块加载 | `LSPosed-Bridge: Loading legacy module com.fktm.tmxydump` + `Hook` |
| cookie 同步 | 32 条；`cookie2` / `munb` 均在；`delivered via broadcast` 通道可用 |
| 门锁抓取 | 开锁后 `doorlocks=1`，`activationKey` 长度 **32**（= 解密后的明文十六进制） |
| Socket | `served envelope to fkTM (doorlocks=N)`，127.0.0.1:42741 正常 |

**第一次启动闪退是 LSPatch 与「apk 里自带 appComponentFactory」的组合问题，不是补丁打坏了。**
天猫校园的 manifest 声明了 `android:appComponentFactory="androidx.core.app.CoreComponentFactory"`，
LSPatch 会把它换成自己的 metaloader 存根；存根初始化时要去原 APK 的 dex 里找这个类，
而首次启动时 `origin.apk` 还没解压完 → `ClassNotFoundException`。第二次启动就有缓存了。

> 想彻底消掉这一次闪退，可以换更新的 **LSPatch v1.2**（`lspatch-v1.2-492.jar`，2026-10），
> 它重写了 metaloader；但 v1.2 打出来的包必须在真机上重新验证一遍，
> 所以当前发布物仍用已验证过的 v0.8。

### 使用顺序（很关键）

1. 装补丁版 → 打开（**若闪退，再点一次图标**）→ 手机号登录；
2. 进「智能宿舍 / 门锁」页 → **真的点一次「开锁」**（门开一下就行，钥匙只在点开锁时下发）；
3. 回到 TMallKey：门锁页会出现这把锁，饮水页会自动带上登录态。

> `doorlocks` 是模块**内存里**的表，天猫校园进程一重启就清空 —— 但 TMallKey 已经把
> 钥匙加密落盘，所以只有「第一次」需要点开锁。换锁 / 换账号才需要再来一次。

## 产物

```
app/build/outputs/apk/release/app-release.apk     # 模块包
```

## 打包命令

```bash
# lspatch.jar 从 https://github.com/JingMatrix/LSPatch/releases/download/v0.8/lspatch.jar
# sha256 = b81094ac3d088849d9781e2678a87562936f8e0d78c4d136a9072dce40f72208
java -jar lspatch-v0.8.jar 天猫校园_5.0.0.apk   -m app/build/outputs/apk/release/app-release.apk   -l 2 --force -o dist/
# 注意 -o 会被当作目录，产物是 dist/<原名>-439-lspatched.apk
```

安装：`adb push` 到手机后用文件管理器装（`adb install` 装 120MB 包会卡死，用
`adb push` + `adb shell pm install -r` 也可以）。**升级安装（同签名）不丢登录态。**

## 数据通道（三次踩坑后定稿）

| 通道 | 结果 |
|---|---|
| 广播 `ACTION_COOKIES` | fkTM 在后台被 ColorOS 冻结时**收不到**（实测静默丢弃） |
| ContentProvider | Android 11+ 包可见性：天猫校园清单没声明 `com.fktm.app`，AMS 直接回 `Unknown authority`（实测）；shell 进程无此限制所以调试时以为能用 |
| **本地回环 Socket** OK | 模块在 `127.0.0.1:42741` 起服务，fkTM 打开饮水页时带 token 连上去拉。回环不经过权限/可见性检查 |

## 模块行为

- hook `Application.attach`：启动 8 秒后开始，前 5 分钟每 20 秒导一次，之后每 60 秒一次
  （进程活着就一直导）。
- 同时开 Socket 服务（守护线程），fkTM 随时来拉。
- 只在拿到 `unb`/`munb`（已登录）时才交付；交付失败不更新缓存，下轮重试。

## fkTM 侧

- `water/CookiePuller.kt`：连 `127.0.0.1:42741`，先发 token 再读一行 JSON。
- `WaterViewModel.syncFromTmall()`：饮水页 `ON_RESUME` 时调用，cookie 变了才落盘刷新。
- `CookieImportReceiver` / `CookieImportProvider`：广播与 Provider 通道保留作备用
  （Provider 在 Android 11+ 跨包访问会被可见性拦掉，仅 shell/同包可用）。

## 风险与边界

- 模块 APK 与补丁包**绑定 LSPatch v0.8（JingMatrix fork）**；官方版没有签名伪装。
- 天猫校园每次更新都要重新下载原版 APK 重新打包。
- Socket 端口任何本地 App 都能连，靠共享 token 防误连 —— 不是强加密边界。
- 重打包仍是修改别人 App，风险自担；出问题卸载后重装原版即可。
