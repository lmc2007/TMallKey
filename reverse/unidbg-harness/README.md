# unidbg-harness — SecurityGuard 官方 SO 黑盒调用

目标：把官方 APK 自带的 SecurityGuard native（`libsgmainso` / `libsgsecuritybodyso` / `libsgmiddletierso`）
搬进 unidbg，用官方签名**生成合法的 `x-mini-wua` / `x-sgext`**，让任意会话完成 CM 层校园建档。
**不逆算法**——SO 是黑盒，只喂参数取返回值。

## 目录

```
tools/unidbg-tmall/libs/       从官方 APK 提取的 SO + yw_1222.jpg（阶段 1 产物）
tools/unidbg-tmall/sgdata/     虚拟 /data/data/com.tmall.campus 映射目录
reverse/unidbg-harness/        本工程（Maven，入库）
  patches/                     unidbg 本地补丁
  src/main/java/com/tmall/
    SGHarness.java             加载 SO、JNI_OnLoad、initialize、doCommand 探针
    SGJni.java                 JNI 垫片（native 回调 Java 层时的实现）
    PlainClassFactory.java     纯 DvmClass 工厂（关掉 Proxy 反射路径）
  bootstrap.sh                 一键：clone → patch → install → 编译
```

## 环境

- JDK 21（`C:/Program Files/Java/jdk-21`）
- Maven 3.9.6，走 `settings.xml` 的阿里云镜像（central 直连超时）
- 网络：GitHub 需要代理 + **openssl 后端**（schannel 握手必失败）：
  `git -c http.sslBackend=openssl -c http.proxy=http://127.0.0.1:7897 clone ...`

## 运行

```bash
cd reverse/unidbg-harness
mvn -s settings.xml -q compile
mvn -s settings.xml -q exec:java                       # 默认：initialize + wua 探针
mvn -s settings.xml -q exec:java -Dexec.args="--verbose"   # 打印全部 JNI 调用
mvn -s settings.xml -q exec:java -Dsg.iolog=true           # 打印文件 IO 重定向
```

## 已确认的事实（2026-10-31 实测）

| 项 | 结果 |
|---|---|
| `libsgmainso-6.5.97.so` 加载 | ✅ arm64，SONAME=`libmain.so`，NEEDED 只有 liblog/libz/libc/libm/libdl |
| `JNI_OnLoad` | ✅ 返回合法 JNI 版本；注册 native 到 `com.taobao.wireless.security.adapter.JNICLibrary` |
| native 入口 | `JNICLibrary.doCommandNative(I[Ljava/lang/Object;)Ljava/lang/Object;` |
| `initialize` | ✅ `doCommand(10101, {Context, int env, appKey, appVersion, cachePath})` 返回 **0** |
| 插件模块 | `libsgcore.so` / `libsgmiddletierso-6.5.90.so` / `libsgsecuritybodyso-6.5.104.so` 均可加载，JNI_OnLoad 通过 |
| `SGPluginExtras.slot` | 各插件在 JNI_OnLoad 里写入自己的分发函数指针（静态字段必须真存，不能返回常量） |
| `getSecurityBodyDataEx` | ❌ `doCommand(20102, …)` 返回 `201029904`（cmd×10000 + 9904） |
| `signRequest` | ❌ `doCommand(10401, …)` 卡在 Map 迭代桩（`HashMap.keySet()` 未实现），当前返回 601 |

## 命令码（从 libsgmain.so 内的 classes.dex 反编译得到，不是猜的）

| 码 | Java 调用点 | 参数 |
|---|---|---|
| 10101 | `SecurityGuardMainPlugin.onPluginLoaded` | `{Context, Integer env, String appKey, String appVersion, String cachePath}` |
| 10401 | `ISecureSignatureComponent.signRequest` | `{Map paramMap, String appKey, int requestType, String authCode, Boolean true}` |
| 20101 | `initSecurityBody(str)` | `{String key, String packageName}` → Boolean |
| 20102 | `ISecurityBodyComponent.getSecurityBodyDataEx` | `{String ts, String appKey, Integer flag, String authCode, String ext, Integer env}` |
| 20103/20104/20105 | putUserTrackRecord / putUserActionRecord / setSecurityBodyServer | — |

mtop 侧来源（`tools/.tmp/cls7_src`）：
`InnerProtocolParamBuilderImpl` → `getSecBodyDataEx(timestamp, appKey, authCode, extMap, **8**)` → `x-mini-wua`。

## 阻塞点

**20102 返回 9904**。主模块的分发表里没有 2xxxx 处理器 —— 插件的命令要由 Java 侧
`SGPluginManager → SecurityGuardSecurityBodyPlugin.onPluginLoaded()` 走完才注册，
而 unidbg 的 DVM **不执行 dex 字节码**（只提供签名/反射桥），所以那段 Java 永远不跑。

三条可行出路（按成本排序）：
1. **直接调 slot**：`SGPluginExtras.slot` 是 body 模块内的分发函数指针（实测 `0x127a4f0b` 落在 body 段内），
   在 IDA 里确认其签名后由 harness 直接调用，绕开主模块分发。
2. **宿主 JVM 跑 Java 层**：`ProxyClassFactory` + 自己实现 Android 框架桩，让真 Java 代码执行
   `onPluginLoaded`。工作量大但最忠实。
3. **改走 middletier**：`IUnifiedSecurityComponent.getUnifiedSign` 同时产出 `x-sgext` / `x-mini-wua`，
   命令码在 15xxx 段，若能直接初始化可绕开 securitybody。

## unidbg 补丁

`patches/unidbg-fstatat-null.patch`：`fstatat64` 对 `pathname == NULL`（AT_EMPTY_PATH 语义）直接 NPE，
SecurityGuard 初始化里必踩。补丁降级为 ENOENT。安装方式见 `bootstrap.sh`。

> 注意：打了补丁必须用 **0.9.10-SNAPSHOT**（本地 `mvn install`），不能用 maven 中央的 0.9.9。

## 行为边界

只读、只本地。不修改天猫校园任何数据；`ck` / `cookie2` 等凭证不进本目录。
