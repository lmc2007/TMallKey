# tmxy-cookie-dump — LSPatch 模块（免 root 登录态同步）

把**天猫校园**（`com.tmall.campus.and`）已登录的 cookie 同步给「快开门」（`com.fktm.app`），
**全程免 root**。

## 实测结论（2026-10-21，OPPO PKR110 / Android 16 / KernelSU）

- LSPatch 打包的天猫校园**能正常打开和登录** —— 前提是用 **JingMatrix/LSPatch v0.8** 且带
  `-l 2`（签名伪装等级 2，把原版签名内嵌进补丁包，SDK 查签名时喂回原值）。
- 官方 LSPosed/LSPatch v0.6 **没有** `-l` 选项，打出来的包一开就崩（阿里安全 SDK 查签名）。
- 模块在天猫校园进程里读 `CookieManager` 拿到 32 条 cookie，通过**本地回环 Socket**
  交给 fkTM，`selfCheck`（`general.device.info`）通过。

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
