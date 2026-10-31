# sg-java — SecurityGuard Java 编排层（跑在宿主 JVM）

## 为什么要有这个模块

native 的 `doCommandNative` 里，命令分发是**线性查表**：

```
for (i = 0; i < count; i++) if (table[i]->cmd == cmd) goto handler;
/* 没找到 */ return err = cmd*10000 + 9904;
```

（`libsgmainso` 0x38F10–0x38F68，实测 `20102` → `201029904`）

也就是说 **2xxxx（securitybody / wua）的处理器要由插件加载流程插进这张表**。而插表动作
在 `SGPluginManager` 这套 Java 编排里 —— unidbg 的 DVM 只提供签名表和反射桥，**不执行 dex**，
所以纯 native 的玩法（手工 `vm.loadLibrary`、调 `SGPluginExtras.slot`、调整加载顺序）
永远补不上这张表。三条路的结论：

| 路线 | 结论 |
|---|---|
| ① 直接调 slot 指针 | ❌ 已实验否定：日志证明 native 根本不 dlopen 插件，绕过 Java 调 slot，表仍然空 |
| ③ 改走 middletier getUnifiedSign | ❌ 同一个病根，15xxx 也要 Java 注册 |
| ② **宿主 JVM 跑 SG 的 Java 层** | ✅ 唯一能补上注册的路径，本项目就是干这个 |

## 内容

- `src/main/java/com/alibaba/wireless/security/mainplugin/**` — 来自 `libsgmain.so`（zip）的 classes.dex
- `src/main/java/com/alibaba/wireless/security/securitybody/**` — 来自 `libsgsecuritybody.so`
- `src/main/java/com/alibaba/wireless/security/middletierplugin/**` — 来自 `libsgmiddletier.so`
- `com/alibaba/wireless/security/open/**` — 只保留**接口** + `SecException` / `SecurityGuardParamContext`
- `com/alibaba/wireless/security/framework/**` — `IRouterComponent` / `ISGPluginInfo` / `SGPluginExtras` + utils
- `com/taobao/wireless/security/sdk/**` — 只保留接口（插件代码用的就是这套命名）
- `framework/utils/UserTrackMethodJniBridge` — 手写桩，关掉 UT 上报
- `open/umid/UMIDComponent` — 手写桩，UMID 关闭

编译基准用 `android.jar`（SDK 33）——里面的方法体是 `Stub!`，**运行时**要由我们自己的
android 实现类覆盖（下一步做）。

## 已裁掉的东西（与 wua 无关，且会拖进 app 私有依赖）

- `middletierplugin/open/fc/**`、`middletierplugin/c/**`、`middletierplugin/open/es/**`（风控 / 弹窗）
- `com/taobao/security/**`（另一套 sdk 命名的 app 侧实现）
- `framework/utils/{a,b,c,d,e}.java`（app 的上报实现）

## 状态

编译错误 259 → **~90**（2026-10-31 16:20）。剩余：

- 60 `cannot find symbol`（多为 android 方法/字段 + 少量 SG 内部互引）
- 10 `int cannot be dereferenced`、8 `non-static variable a`（jadx 反编译产物畸变，逐个手修）
- 8 `method does not override`（接口与实现签名对不上，多为 `throws` 差异）

## 下一步

1. 把剩余 ~90 个编译错误清掉，`mvn package` 出 jar。
2. 写 `android-impl`：只实现 SG 真正碰到的少数类（`Context` / `SharedPreferences` /
   `PackageManager` / `ApplicationInfo` / `Build` / `SystemClock` / `Log`），classpath 排在
   `android.jar` 之前覆盖 `Stub!`。
3. harness 切 `ProxyClassFactory`（fallback = 现有 SGJni），由真 Java 驱动：
   `SecurityGuardMainPlugin.onPluginLoaded` → `SecurityGuardSecurityBodyPlugin.onPluginLoaded`
   → `getInterface(ISecurityBodyComponent).getSecurityBodyDataEx(..., 8)`。
4. 验收：`20102` 返回 wua 字符串；再比对真机抓包格式。

## 注意

- 这些源码是 jadx 反编译产物（Apache-2.0 的 unidbg 只是工具链，SG 本身是闭源商业 SDK），
  **只在本机跑、不进产品**，最终只把生成的头拿去用。
- `open/initialize/c.java` 里有一份官方签名 SHA-256 白名单（SG 会校验宿主 APK 签名）——
  宿主 JVM 没有 APK 签名，跑到校验那步要 stub 掉（记着这个坑）。
