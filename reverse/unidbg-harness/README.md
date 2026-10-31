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

---

## 路线②：宿主 JVM 跑 SG 的 Java 编排层（2026-10-31 下午）

`SGRunner` + `RunJava` 是这条路的主入口：

```
SG Java（宿主 JVM） --NativeBridge--> unidbg doCommandNative --> 官方 SO
      ^                                                            |
      +---------- ProxyJni 反射回调（FindClass / CallStaticXxx）----+
```

### 组件

| 件 | 作用 |
|---|---|
| `reverse/sg-java` | SG 三个插件 dex 的 Java 层（编译通过，154 class） |
| `reverse/android-impl` | android.* 的可用实现（Context / SharedPreferences / pm.* / os.* / telephony 等） |
| `SGRunner` | 装 ProxyClassFactory（fallback=SGJni）、挂 NativeBridge、挂 loadLibrary 钩子、驱动插件加载 |
| `NativeLibraryLoader` | 把插件 Java 里的 `System.loadLibrary` 接到 unidbg 的 `vm.loadLibrary + JNI_OnLoad` |
| `NativeBridge` | 把 `JNICLibrary.doCommandNative`（原本是 native）转发进 unidbg |

### 已验证

- `SecurityGuardMainPlugin.onPluginLoaded` 跑通，`native doCommand(10101)` 经 Java 调用返回 **0**
- `SecurityGuardSecurityBodyPlugin.onPluginLoaded` 跑通，`loadLibrary(sgsecuritybody)` 真的加载了
  `libsecuritybody.so` 并跑 JNI_OnLoad，**`SGPluginExtras.slot` 被写入 `0x13284f0b`**（body 段内）
- 异常能取到：native 抛的 `SecException` 会被 unidbg 吞成 null，靠 `SecException.lastErrorCode` 抓

### 仍然卡住：2xxxx 还是 9904

`20101` / `20102` 都返回 `SecException(201019904 / 201029904)`，即查表仍未命中。

结论：**分发表不是插件 JNI_OnLoad 填的**。body 的 JNI_OnLoad 只写了 `SGPluginExtras.slot`，
而主模块在处理 2xxxx 时并没有去读这个字段（verbose 里只有 Set，没有 Get）。

真正的插表动作应该在框架里：`SGPluginManager` 调 `onPluginLoaded` 拿到返回的 `IRouterComponent`，
再把它交给 native 登记。但我们在 decompiled 的 dex 里**找不到任何 `onPluginLoaded` 的调用点**
（classes8 的框架类有 27 个类 jadx 反编译失败，`d.java` 1295 行里有多处 JADX WARN），
大概率就丢在那部分。

### 下一步（二选一）

1. **补 decompile**：对 `classes8.dex` 用 jadx 的 `--show-bad-code` 或换 baksmali，把 `d.java`
   里未反编译的方法拿全，找到“onPluginLoaded → 交给 native”的那一步，照着补一次。
2. **直接在 native 侧找登记入口**：主模块里除 `doCommandNative` 外还有 `setGlobalJVM` 导出；
   在 IDA 里看 2xxxx 的 handler 是不是从某个全局表里取函数指针，以及谁往里写。

---

## ★ 2026-10-31 16:50 打通：10102 是插件登记命令

### 结论

`framework/d.java`（需 `jadx --show-bad-code`，普通模式会丢这段）里，框架对**每个插件**在
`onPluginLoaded` 之后都会发一次登记命令：

```java
} else {
    SGPluginExtras.slot = 0L;                                    // 加载后续插件前清零
    plugin.onPluginLoaded(context, this.f, info, name, flags);   // 插件内 loadLibrary → JNI_OnLoad 写 slot
}
...
r.doCommand(10102, { 插件名, str11, so 绝对路径 });
```

- 插件 so 的 `JNI_OnLoad` **只**把分发函数指针写进 `SGPluginExtras.slot`（实测 `0x13284f0b`）。
- **真正把 2xxxx 插进主模块分发表的是随后的 10102**。
- 手工驱动时漏掉 10102 → 分发表永远空 → 任何 2xxxx 都返回 `cmd*10000+9904`。

### 正确初始化顺序（`SGRunner.initPlugins`）

```
10101 main 初始化 → main 插件 onPluginLoaded（router=null，返回 router）
→ body 插件 onPluginLoaded（传入 router；内部 loadLibrary → JNI_OnLoad 写 slot）
→ 10102 {name, "", soPath} 登记
→ 20101 initSecurityBody(appKey, pkg)
→ 20102 出 wua
```

### 实测输出

```
[RUN] native doCommand(10101) -> 0
[RUN] native doCommand(10102) -> 0   slot=13284f0b
[RUN] native doCommand(20101) -> true
[RUN] native doCommand(20102) -> 51 字节 / 69 字符 base64 (flag=8)
```

同一参数连打三次输出各不相同（活计算，带随机 nonce）。
`flag=4` / `flag=1` 报 `SecException(1412)`（参数要求不同，未展开）。

## wua 命令行（阶段 5 集成形态）

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
export MAVEN_OPTS="--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"

cd reverse/unidbg-harness
mvn -q exec:java -Dexec.mainClass=com.tmall.WuaCli -Dexec.args="12574478 8"
# __WUA_JSON__ {"ok":true,"appKey":"12574478","flag":8,"ts":"...","wua":"...","len":69,"initMs":1204,"genMs":209}
```

- 初始化 ~1.2s，单次 wua ~210ms。
- `--add-opens java.base/java.util` 是**必需**的：native 要遍历 `paramMap`（`HashMap$KeySet.toArray`），
  JDK 21 模块系统默认拦掉，报 `InaccessibleObjectException`（表现为 doCommand 静默返回 null）。
- `Runtime.halt()` 收尾：unicorn 在 JVM 正常 shutdown 时会 AV 崩溃，halt 跳过该流程。

## 服务端验证工具

`reverse/wua_probe.py`：

```bash
python reverse/wua_probe.py --gen-only                     # 只产 wua
python reverse/wua_probe.py --cookies cookies.txt \
    --api mtop.tmall.campus.member.app.user.get --compare  # 带/不带 wua 对比
```

注意：`mtop.common.getTimestamp` 这类公开接口**不校验** wua（带与不带都 SUCCESS），
所以「没有报错」不代表 wua 有效。必须用需要登录态、且会校验反作弊头的接口。

## 未解决

1. **wua 是否被服务端接受** —— 待真实会话验证（阶段 4）。
2. `10401 signRequest` 一律 `SecException(608)`（所有 requestType），疑与
   `open/initialize/c.java` 的官方 APK 签名白名单有关（宿主 JVM 无 APK 签名）。属附加项，暂不展开。

---

## wua 常驻服务（阶段 5 集成形态）

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
export MAVEN_OPTS="--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
cd reverse/unidbg-harness
mvn -q exec:java -Dexec.mainClass=com.tmall.WuaServer -Dexec.args="17321"

curl "http://127.0.0.1:17321/healthz"
curl "http://127.0.0.1:17321/wua?appKey=12574478&flag=8"
# {"ok":true,"wua":"aQgRjUCxvPj9/...","len":69,"ts":"...","flag":8,"genMs":110}
```

- 只绑 127.0.0.1，不对外。
- 初始化一次 ~2.7s；之后每次 110~270ms（对比 CLI 每次要 4s）。
- 处理线程固定 1 个：native 不是线程安全的。

## 10102 的第二参数 = 插件版本号

`d.java` 里 `String str29 = "libsg" + str + "so-" + str3 + ".so"; str11 = str3;`，
所以 `10102(name, version, soPath)`。传空串时 body 仍能工作，但 middletier 会校验。

## middletier（x-sign / wua / x-mini-wua / x-umt / x-sgext 的统一入口）

mtop 一次请求要 4 个头，且 **x-sign 为空时整个请求直接失败**（`InnerProtocolParamBuilderImpl`
236-246 行）。它们全来自 `IUnifiedSecurityComponent.getSecurityFactors()` →
middletier 的 **70102**。`70101` 是 `init(authCode, flag)`。

已把 middletier 的 Java 层编进 `reverse/sg-java` 并接入 Runner，但：

| 命令 | 结果 |
|---|---|
| 70101 init | ❌ `SecException(2403)` |
| 70102 getSecurityFactors | ❌ `SecException(2403)` |
| 10401 signRequest | ❌ `SecException(608)`（所有 requestType） |

**2403 的定位**：verbose 显示 middletier 的 handler（libmiddletier 0x4e63c 读参数）执行后，
异常由 **libmain 0x11f740** 构造（`SecException("", 0x963)`）—— 即 middletier 的 handler
返回了 null，主模块把它转成 2403。同理 608 也由 0x11f740 抛出。

**已排除**：
- 不是 `slot` 问题：加 `SGPluginExtras.slot = 0L` 后 middletier 写入 `0x13ec8797`（落在自身段内）
- 不是 10102 参数问题：版本号传对、返回 0
- 不是顺序问题：20101 前移、main 也登记，均无效
- 不是文件缺失：给 Runner 补了 IOResolver（`/data/data/<pkg>` → sgdata，写模式自动建文件），
  实测 **IO-MISS = 0**，SG 根本没读这些路径

**未排除**：middletier handler 内部的某种前置（AVMP/配置/umid）。属附加项。

## 一个值得记住的 jadx 缺陷

`middletierplugin/d/d/a.java` 的 `invoke` 里，jadx 把 switch 的 `break` 丢了：

```java
if (hashCode == 165313390 && name.equals("getSecurityFactors")) { sel = 1; }
sel = 65535;      // ← 无条件覆盖，任何调用都 return null
```

补成 `else { sel = 65535; }` 后 70101/70102 才真正被调用到。**同类 "invoke + hashCode 分派"
的 jadx 产物都要检查这个模式**（本工程里还有 C0000/C0001/C0002 等）。

## 主线不受 10401/70102 阻塞的原因

CM 建档只校验 `x-mini-wua`（20102）。`x-sign` 可以用 h5 的 `_m_h5_tk` token 做
`md5(token&t&appKey&data)`（`fktm/water/mtop.py` 现有链路就是这么算的），不需要 SG。

---

## 2026-10-31 晚：四条关键修正（appKey / wua 长度 / middletier 入口 / AVMP）

### 1. appKey 是 34270422

设备 `shared_prefs/MtopConfigStore.xml` 的 `MTOPSDK_DEVICEID_STORE.34270422deviceId`
暴露了真实 appKey；`ACCS_SDK.xml` 里也有 `appkey=34270422`。
`12574478` 在整个 APK 的 dex 里**一次都没出现**。

代码里已统一为 `SGRunner.APP_KEY`（可用 `-Dsg.appkey=` 覆盖）。

### 2. x-mini-wua 的 69 字符是正常长度

四种参数组合（ts/appKey/authCode/ext 各种 null 与非 null）输出**全部是 69 字符**。
51 字节明文就是这个 SG 版本的定长格式，不是"设备信息缺失被压缩"。
CSDN 文章里"短 wua 不能用"说的是 `wua` 头（AVMP 那条），不是 `x-mini-wua`。

### 3. middletier 的入口是 IMiddleTierGenericComponent

```
getSign({data, env, appkey, auth_code})  → x-sign     内部就是 signRequest(type=7)
getWua({data, env})                       → wua        AVMP: invokeAVMP2("sign_v2", ...)
getMiniWua({env, extend_paras})           → x-miniwua 内部 getSecurityBodyDataEx(null,null,null,ext,8,env)
```

对应实现是被我裁掉的 `middletierplugin/d/b/a.java`。`getSign` 只是 signRequest 包装，
所以 **x-sign 仍然卡在 10401**，与 middletier 无关。

### 4. AVMP（模块 6）根本没注册；10401 是全局 gate

```
60101/60102/60201/60202/60206/60401/60402 → SecException(cmd*10000+9904)   没有 handler
70201 createAVMPInstance                  → SecException(1905)
10401  requestType 1..10                  → 全部 608（5~25ms，没算任何东西）
```

- 模块 6 = AVMP，在 unidbg 环境里从未注册（移走 `SG_INNER_DATA_AVMP` 让它重建也无效）。
- 608 与 appKey / INPUT / requestType **全部无关**，是 signRequest 入口的前置检查。
- `-Dsg.apkmap=true` 映射完整 APK：模块 6 仍 9904，且随后 unicorn `Invalid memory read` 崩溃。

### 5. 两条后端都不支持 code hook

| 后端 | 结果 |
|---|---|
| unicorn2 | 装上不回调（构造期 / 首次 dispatch 惰性装都试过） |
| dynarmic（`-Dsg.backend=dynarmic`） | `hook_add_new` 抛 `UnsupportedOperationException` |

所以 `0x11F73C` 的 `W20`、`sub_39338` 的内部状态都读不到；`traceCode` 太慢不实用。

## 快速入口（推荐日常用这个）

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"
export MAVEN_OPTS="--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
cd reverse/unidbg-harness
mvn -q exec:java -Dexec.mainClass=com.tmall.QuickRun     # 7~15 秒跑完
```

`QuickRun` 会做：initPlugins → 四种 20102 参数组合 → 模块 6/7 命令探测 → 10401 requestType 1..10。
比 `RunJava`（3~4 分钟）快一个数量级，适合快速试参数。

---

## ★ 真机 hook（frida）—— 2026-10-31 19:20 打通

不需要 frida-gadget、不需要改包。之前失败是**用法问题**，天猫校园并没有拦住我们。

### 四个必要条件（每个都踩过）

1. **frida-server 要让 adb shell 一直挂着**
   ```bash
   adb shell "su -c '/data/local/tmp/frida-server -l 127.0.0.1:27042'"   # 前台跑，另开一个终端操作
   ```
   `nohup setsid ... &` 会被 su 会话清理，进程和日志都不存在。
2. **attach 主进程**（`pidof com.tmall.campus.and`）。
   `frida -f` 的 spawn 会变成 `:channel` 子进程，别用。
   frida 的 stdin 必须保持打开：`sleep 180 | frida -U -p <pid> -l x.js -q`，否则 REPL 立刻退出。
3. **SG 的类在自定义 ClassLoader 里**
   `com.taobao.wireless.security.adapter.JNICLibrary` 只在
   `PathClassLoader[DexPathList[zip .../lib/arm64/libsgmain.so]]` 里；
   必须先 `Java.classFactory.loader = <该 loader>`，再 `Java.use(...)`。
4. **日志写 App 私有目录**（`ctx.getFilesDir()`）；`/data/local/tmp` 被 SELinux 拦。
   `setTimeout` 在这个场景不触发 → 脚本要**同步执行**。

### 脚本

| 文件 | 作用 |
|---|---|
| `reverse/hook_discover.js` | 枚举 SG 的类 / ClassLoader / 已加载 so |
| `reverse/hook_watch.js` | 监看 App 自己的 `doCommandNative` 调用（写 `files/sg_watch.log`） |
| `reverse/hook_sg5.js` | hook + 主动在真机上调 `signRequest`（拿基准用） |
| `reverse/hook_headers.js` | 抓请求头（anet/okhttp 的 addHeader；目前未命中，待换 hook 点） |

### 真机基准（appKey 34270422，INPUT=`test_input_for_sign`）

```
10401 {INPUT=...}, 34270422, 7, "", true  ->  ab2e750100be14e0b173c648b2ef0a92ed99145e595b87c438
10401 {INPUT=...}, 34270422, 3, "", true  ->  d929eaee7285e33c65d764d940376ba79d801b02
10401 {INPUT=...}, 34270422, 2, "", true  ->  0CCBE379C55EE9E7B82CF82F5223F746
```

**结论**：
- 我们调 10401 的参数形式与真机**完全一致** → 608 不是调用方式问题。
- `type=2` ≠ `md5(INPUT)`（md5 = `b6f7cf53e0c71d53ea940785a8ab52e9`）→ SG 用**设备绑定密钥**
  做 HMAC，不是裸哈希。跨环境算不出同值是必然的。
- 所以 608 的唯一合理解释是：**取密钥失败**（安全图片 / AVMP / SG 数据三者之一没就绪）。
