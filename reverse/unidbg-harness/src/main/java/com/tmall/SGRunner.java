package com.tmall;

import android.content.Context;

import com.alibaba.wireless.security.framework.ISGPluginInfo;
import com.alibaba.wireless.security.framework.ISGPluginManager;
import com.alibaba.wireless.security.framework.IRouterComponent;
import com.alibaba.wireless.security.mainplugin.C0001;
import com.alibaba.wireless.security.mainplugin.SecurityGuardMainPlugin;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;
import com.alibaba.wireless.security.securitybody.SecurityGuardSecurityBodyPlugin;
import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.jni.ProxyClassFactory;
import com.github.unidbg.linux.android.dvm.jni.ProxyDvmObject;
import com.github.unidbg.linux.android.dvm.wrapper.DvmBoolean;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.linux.android.dvm.wrapper.DvmLong;
import com.github.unidbg.memory.Memory;

import java.io.File;
import java.io.IOException;

/**
 * 路线② 的主入口：SG 的 Java 编排层跑在宿主 JVM，native 由 unidbg 提供。
 *
 *   SG Java（宿主 JVM） → NativeBridge → unidbg doCommandNative → 官方 SO
 *        ↑                                                           │
 *        └──────── ProxyJni 反射回调（FindClass / CallStaticMethod）──┘
 *
 * 关键点：插件加载流程必须由 SG 自己的 Java 代码走完，2xxxx（securitybody/wua）的处理器
 * 才会被注册进 native 的命令表。手工调 doCommand 永远补不上（9904 = 查表未命中）。
 */
public class SGRunner {

    public static final String LIB_DIR = SGHarness.LIB_DIR;
    public static final String PKG = SGHarness.PKG;

    /** 挂在字段上：run() 里的 sign 探针要用 */
    private SecurityGuardMainPlugin mainPlugin;
    /** middletier：getSecurityFactors 一次产出 x-sign / wua / x-mini-wua / x-umt / x-sgext */
    private com.alibaba.wireless.security.middletierplugin.SecurityGuardMiddleTierPlugin mtPlugin;
    private IRouterComponent router;
    private final AndroidEmulator emulator;
    private final VM vm;
    private Module mainModule;
    private final SGJni jni;
    private final DvmClass jniLibrary;
    private final Context context;

    public SGRunner(boolean verbose) throws IOException {
        emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName(PKG)
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new LibDirResolver(
                new File(LIB_DIR).getAbsoluteFile(), new AndroidResolver(23)));

        // 文件重定向：SG 初始化/签名要读写 /data/data/<pkg> 下的缓存，不映射就全部 MISS
        File dataRoot = new File(SGHarness.DATA_DIR);
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            System.out.println("[RUN] cannot create data dir " + dataRoot);
        }
        emulator.getSyscallHandler().addIOResolver(new com.github.unidbg.file.IOResolver<com.github.unidbg.file.linux.AndroidFileIO>() {
            @Override
            public com.github.unidbg.file.FileResult<com.github.unidbg.file.linux.AndroidFileIO> resolve(
                    com.github.unidbg.Emulator<com.github.unidbg.file.linux.AndroidFileIO> emu,
                    String pathname, int oflags) {
                if (Boolean.getBoolean("sg.iolog")) {
                    System.out.println("[IO-REQ] " + pathname + "  flags=0x" + Integer.toHexString(oflags));
                }
                // SG 从 APK 里读安全图片（res/drawable/yw_1222.jpg）。日志显示 native 会反复打开
                // /data/app/<pkg>-1/base.apk，不映射过去就拿不到安全图片 → 所有签名失败。
                if (Boolean.getBoolean("sg.apkmap") && pathname.startsWith("/data/app/") && pathname.endsWith(".apk")) {
                    File apk = resolveApk();
                    if (apk != null) {
                        System.out.println("[IO] map " + pathname + " -> " + apk);
                        return com.github.unidbg.file.FileResult.success(
                                new com.github.unidbg.linux.file.SimpleFileIO(oflags, apk, pathname));
                    }
                    System.out.println("[IO-MISS] apk " + pathname + "（找不到本地 APK）");
                    return null;
                }
                // 插件 so 的 Android 路径：10102 登记时 native 要按这个路径去 open
                String libPrefix = "/data/app/" + PKG + "-1/lib/arm64/";
                if (pathname.startsWith(libPrefix)) {
                    File host = new File(LIB_DIR, pathname.substring(libPrefix.length()));
                    if (host.isFile()) {
                        return com.github.unidbg.file.FileResult.success(
                                new com.github.unidbg.linux.file.SimpleFileIO(oflags, host, pathname));
                    }
                    if (Boolean.getBoolean("sg.iolog")) {
                        System.out.println("[IO] lib-miss " + pathname);
                    }
                    return null;
                }

                // /dev/__properties__：SG 在 native 侧直接读 Android property（不走 Java Build）。
                // 把真机拉下来的 prop area 喂给它，环境判定才可能与真机一致。
                if (pathname.startsWith("/dev/__properties__")) {
                    File propDir = new File(dataRoot, "properties");
                    String rel = pathname.substring("/dev/__properties__".length()).replaceFirst("^/+", "");
                    if (rel.isEmpty()) {
                        // 必须对外暴露 Android 的原始文件名（含冒号），否则 bionic 找不到对应 context 的 area
                        java.util.List<com.github.unidbg.linux.file.DirectoryFileIO.DirectoryEntry> list =
                                new java.util.ArrayList<>();
                        list.add(new com.github.unidbg.linux.file.DirectoryFileIO.DirectoryEntry(true, "property_info"));
                        list.add(new com.github.unidbg.linux.file.DirectoryFileIO.DirectoryEntry(true, "properties_serial"));
                        for (String c : new String[]{"build_prop", "default_prop", "system_prop",
                                "vendor_default_prop"}) {
                            list.add(new com.github.unidbg.linux.file.DirectoryFileIO.DirectoryEntry(
                                    true, "u:object_r:" + c + ":s0"));
                        }
                        System.out.println("[IO] prop dir -> " + list.size() + " entries");
                        return com.github.unidbg.file.FileResult.success(
                                new com.github.unidbg.linux.file.DirectoryFileIO(
                                        oflags, pathname,
                                        list.toArray(new com.github.unidbg.linux.file.DirectoryFileIO.DirectoryEntry[0])));
                    }
                    String local = rel;
                    if (rel.startsWith("u:object_r:")) {
                        String inner = rel.substring("u:object_r:".length());
                        int c = inner.indexOf(':');
                        if (c > 0) {
                            inner = inner.substring(0, c);
                        }
                        local = "p_" + inner;
                    }
                    File f = new File(propDir, local);
                    if (f.isFile()) {
                        if (Boolean.getBoolean("sg.iolog")) {
                            System.out.println("[IO] prop " + pathname + " -> " + local);
                        }
                        return com.github.unidbg.file.FileResult.success(
                                new com.github.unidbg.linux.file.SimpleFileIO(oflags, f, pathname));
                    }
                    if (Boolean.getBoolean("sg.iolog")) {
                        System.out.println("[IO] prop-missing " + pathname);
                    }
                    return null;
                }

                // SG 会用相对路径 open（内部先 chdir 到数据目录），形如 "5.0.0/SG_INNER_DATA_AVMP"。
                // 真机数据落在 <data>/app_SGLib/ 下且没有版本子目录，所以这里做两级映射。
                if (!pathname.startsWith("/")) {
                    File base = new File(dataRoot, "app_SGLib");
                    File cand = new File(base, pathname);
                    if (!cand.isFile()) {
                        int slash = pathname.indexOf('/');
                        if (slash > 0) {
                            cand = new File(base, pathname.substring(slash + 1));
                        }
                    }
                    if (cand.isFile()) {
                        return com.github.unidbg.file.FileResult.success(
                                new com.github.unidbg.linux.file.SimpleFileIO(oflags, cand, pathname));
                    }
                    File parent = cand.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    if ((oflags & 3) != 0) {
                        try {
                            if (cand.createNewFile()) {
                                return com.github.unidbg.file.FileResult.success(
                                        new com.github.unidbg.linux.file.SimpleFileIO(oflags, cand, pathname));
                            }
                        } catch (java.io.IOException ignored) {
                        }
                    }
                    System.out.println("[IO-MISS-REL] " + pathname + " -> " + cand);
                    return null;
                }
                String prefix = "/data/data/" + PKG + "/";
                if (!pathname.startsWith(prefix)) {
                    return null;
                }
                File host = new File(dataRoot, pathname.substring(prefix.length()));
                boolean wantDir = (oflags & 0x10000) != 0;
                if (Boolean.getBoolean("sg.iolog")) {
                    System.out.println("[IO] open " + pathname + " oflags=0x" + Integer.toHexString(oflags)
                            + " host=" + (host.isDirectory() ? "dir" : host.isFile() ? "file" : "none"));
                }
                if (host.isDirectory()) {
                    return com.github.unidbg.file.FileResult.success(
                            new com.github.unidbg.linux.file.DirectoryFileIO(oflags, pathname, host));
                }
                if (!host.exists() && (oflags & 3) != 0) {
                    File parent = host.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try {
                        if (host.createNewFile()) {
                            return com.github.unidbg.file.FileResult.success(
                                    new com.github.unidbg.linux.file.SimpleFileIO(oflags, host, pathname));
                        }
                    } catch (java.io.IOException e) {
                        System.out.println("[IO] create failed " + host + ": " + e.getMessage());
                    }
                }
                if (host.isFile() && wantDir) {
                    return null;
                }
                if (host.isFile()) {
                    return com.github.unidbg.file.FileResult.success(
                            new com.github.unidbg.linux.file.SimpleFileIO(oflags, host, pathname));
                }
                if ((oflags & 3) != 2) {
                    System.out.println("[IO-MISS] open " + pathname + " (oflags=0x" + Integer.toHexString(oflags) + ")");
                }
                return null;
            }
        });

        vm = emulator.createDalvikVM();
        vm.setVerbose(verbose);

        // 顺序：先装自己的 Jni（兜底 android 桩），再装 ProxyClassFactory（反射到宿主 JVM）
        jni = new SGJni(emulator, vm, PKG, false);
        vm.setJni(jni);
        ProxyClassFactory factory = new ProxyClassFactory();
        factory.setFallbackJni(jni);
        vm.setDvmClassFactory(factory);

        File so = new File(LIB_DIR, SGHarness.SGMAIN);
        DalvikModule dm = vm.loadLibrary(so, true);
        Module module = dm.getModule();
        mainModule = module;
        System.out.println("[RUN] loaded " + module.name + " base=0x" + Long.toHexString(module.base));

        jniLibrary = vm.resolveClass(SGHarness.JNI_CLASS);
        dm.callJNI_OnLoad(emulator);
        System.out.println("[RUN] JNI_OnLoad ok");

        // 查表指令处 hook：38F44 是 CMP W10, W23（W23=命令码，X10=entry 指针）。
        // 命令表是运行时构造的，静态数据里搜不到，只能动态读。
        if (Boolean.getBoolean("sg.tracetable")) {
            final Module tm = module;
            try {
                emulator.getBackend().hook_add_new(new com.github.unidbg.arm.backend.CodeHook() {
                    @Override
                    public void hook(com.github.unidbg.arm.backend.Backend backend, long address, int size, Object user) {
                        try {
                            long cmd = backend.reg_read(unicorn.Arm64Const.UC_ARM64_REG_X23).longValue() & 0xffffffffL;
                            long entry = backend.reg_read(unicorn.Arm64Const.UC_ARM64_REG_X10).longValue();
                            if (cmd < 10000 || cmd > 40000) {
                                return;
                            }
                            long handler = 0;
                            try {
                                handler = emulator.getMemory().pointer(entry).getLong(0);
                            } catch (Throwable ignored) {
                            }
                            System.out.println("[TBL] cmd=" + cmd + " entry=0x" + Long.toHexString(entry)
                                    + " handler=0x" + Long.toHexString(handler)
                                    + (handler > tm.base && handler < tm.base + 0x200000L
                                    ? " (libmain+0x" + Long.toHexString(handler - tm.base) + ")" : ""));
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override
                    public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {
                    }

                    @Override
                    public void detach() {
                    }
                }, module.base + 0x3b34cL, module.base + 0x3b350L, null);
                System.out.println("[RUN] table hook installed at 0x3b34c (entry probe)");
            } catch (Throwable t) {
                System.out.println("[RUN] table hook failed: " + t);
            }
        }

        // 0x11F740 是 libmain 里构造并抛出 SecException 的公共点。
        // addBreakPoint 会抛 InvalidMemoryAccessException，改用 CodeHook 抓 LR。
        if (Boolean.getBoolean("sg.throwbp")) {
            final Module m = module;
            emulator.getBackend().hook_add_new(new com.github.unidbg.arm.backend.CodeHook() {
                @Override
                public void hook(com.github.unidbg.arm.backend.Backend backend, long address, int size, Object user) {
                    long lr = emulator.getContext().getLR();
                    System.out.println("[HOOK-THROW] pc=0x" + Long.toHexString(address - m.base)
                            + " LR=0x" + Long.toHexString(lr)
                            + " (off 0x" + Long.toHexString(lr - m.base) + ")");
                }

                @Override
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {
                }

                @Override
                public void detach() {
                }
            }, module.base + 0x11f740L, module.base + 0x11f744L, null);
            System.out.println("[RUN] throw codehook installed at 0x11f740");
        }

        // 必须用 Application：SG 里多处判断 context instanceof Application
        context = new android.app.Application(PKG, "/data/data/" + PKG);

        // Java → native 的桥
        com.tmall.sghook.NativeBridge.setHandler(this::dispatch);

        // System.loadLibrary 的钩子：Java 说“加载 sgmain/sgsecuritybody”，这里真的在 unidbg 里加载，
        // 并执行 JNI_OnLoad —— 插件的 SGPluginExtras.slot（分发函数指针）就是这时候写进去的。
        com.tmall.sghook.NativeLibraryLoader.setHook(name -> {
            String file = soFileOf(name);
            if (file == null) {
                System.out.println("[RUN] loadLibrary(" + name + ") 无对应 so，跳过");
                return;
            }
            try {
                DalvikModule m = vm.loadLibrary(new File(LIB_DIR, file), true);
                m.callJNI_OnLoad(emulator);
                System.out.println("[RUN] loadLibrary(" + name + ") -> " + m.getModule().name
                        + " base=0x" + Long.toHexString(m.getModule().base));
            } catch (Throwable t) {
                System.out.println("[RUN] loadLibrary(" + name + ") 失败: " + t);
            }
        });
    }

    /** 从工作目录往上找官方 APK（安全图片就在它里面）。 */
    private static File resolveApk() {
        String explicit = System.getProperty("sg.apk");
        if (explicit != null) {
            File f = new File(explicit);
            return f.isFile() ? f : null;
        }
        // 优先用 ASCII 文件名：SimpleFileIO 打不开含中文的路径（会报"拒绝访问"）
        for (String prefix : new String[]{"", "../", "../../", "../../../", "../../../../"}) {
            for (String name : new String[]{"tools/unidbg-tmall/tmall-base.apk", "天猫校园.apk"}) {
                File f = new File(prefix + name);
                if (f.isFile()) {
                    try {
                        return f.getCanonicalFile();
                    } catch (java.io.IOException e) {
                        return f.getAbsoluteFile();
                    }
                }
            }
        }
        return null;
    }

    private static String md5Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 插件名 → 提取出来的 so 文件名。 */
    private static String soFileOf(String name) {
        String n = name == null ? "" : name;
        if (n.contains("main") || n.equals("sgmain")) {
            return "libsgmainso-6.5.97.so";
        }
        if (n.contains("middletier")) {
            return "libsgmiddletierso-6.5.90.so";
        }
        if (n.contains("securitybody") || n.contains("body")) {
            return "libsgsecuritybodyso-6.5.104.so";
        }
        if (n.contains("core")) {
            return "libsgcore.so";
        }
        return null;
    }

    /** 宿主 JVM 里的 SG 代码回调进来：把 Java 对象包成 DVM 对象，再进 unidbg。 */
    private boolean tableHookInstalled = false;

    /** 在第一次 dispatch 时装 hook（构造期装了不触发）。 */
    private void installEntryHook() {
        if (tableHookInstalled || mainModule == null) {
            return;
        }
        tableHookInstalled = true;
        final Module tm = mainModule;
        try {
            emulator.getBackend().hook_add_new(new com.github.unidbg.arm.backend.CodeHook() {
                @Override
                public void hook(com.github.unidbg.arm.backend.Backend backend, long address, int size, Object user) {
                    System.out.println("[TBL] hit pc=0x" + Long.toHexString(address - tm.base));
                }

                @Override
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {
                }

                @Override
                public void detach() {
                }
            }, tm.base + 0x3b34cL, tm.base + 0x3b350L, null);
            System.out.println("[RUN] entry hook installed (lazy)");
        } catch (Throwable t) {
            System.out.println("[RUN] entry hook failed: " + t);
        }
    }

    private Object dispatch(int code, Object[] args) {
        if (Boolean.getBoolean("sg.tracetable") && code == 10401) {
            // hook_add_new 在 unicorn2 后端不回调，改用内置 trace 看真实执行路径
            emulator.traceCode(mainModule.base + 0x3b34cL, mainModule.base + 0x3b4d0L);
        }
        DvmObject<?>[] params = new DvmObject<?>[args == null ? 0 : args.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = toDvm(args[i]);
        }
        long t0 = System.currentTimeMillis();
        Object ret = jniLibrary.callStaticJniMethodObject(emulator, SGHarness.DO_COMMAND_SIG,
                code, new ArrayObject(params));
        String err = "";
        int errCode = com.alibaba.wireless.security.open.SecException.lastErrorCode;
        if (errCode != 0 && ret == null) {
            err = "   [native 抛出 SecException(" + errCode + ") "
                    + com.alibaba.wireless.security.open.SecException.lastMessage + "]";
            com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
        }
        System.out.println("[RUN] native doCommand(" + code + ") -> " + SGHarness.describe(ret)
                + "  " + (System.currentTimeMillis() - t0) + "ms" + err);
        return unwrap(ret);
    }

    private DvmObject<?> toDvm(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof DvmObject) {
            return (DvmObject<?>) o;
        }
        if (o instanceof String) {
            return new StringObject(vm, (String) o);
        }
        if (o instanceof Integer) {
            return DvmInteger.valueOf(vm, (Integer) o);
        }
        if (o instanceof Long) {
            return DvmLong.valueOf(vm, (Long) o);
        }
        if (o instanceof Boolean) {
            return DvmBoolean.valueOf(vm, (Boolean) o);
        }
        if (o instanceof byte[]) {
            return new ByteArray(vm, (byte[]) o);
        }
        // Context / Map / 其它对象：包成代理对象，native 回调时反射回宿主实例
        return ProxyDvmObject.createObject(vm, o);
    }

    private Object unwrap(Object ret) {
        if (ret instanceof DvmObject) {
            return ((DvmObject<?>) ret).getValue();
        }
        return ret;
    }

    /**
     * 按真机顺序初始化：main 插件 → body 插件 → 10102 登记 → 20101 initSecurityBody。
     * 返回可直接调 wua 的 body 组件（失败返回 null）。
     */
    public ISecurityBodyComponent initPlugins() {
        router = new C0001();

        System.out.println("[RUN] === SecurityGuardMainPlugin.onPluginLoaded ===");
        mainPlugin = new SecurityGuardMainPlugin();
        ISGPluginInfo mainInfo = new PluginInfo("sgmain", "6.5.97", mainPlugin);
        try {
            // 第 8 个参数是 SG 的数据根目录：真机是 <data>/app_SGLib（不是 files）
            mainPlugin.onPluginLoaded(context, router, mainInfo, "sgmain", 0,
                    "12574478", "5.0.0", "/data/data/" + PKG + "/app_SGLib");
            String mainSo = "/data/app/" + PKG + "-1/lib/arm64/libsgmainso-6.5.97.so";
            Object m0 = router.doCommand(10102, "sgmain", "6.5.97", mainSo);
            System.out.println("[RUN] register main(10102) -> " + SGHarness.describe(m0));
            System.out.println("[RUN] main plugin loaded");
        } catch (Throwable t) {
            System.out.println("[RUN] main plugin FAILED: " + t);
            t.printStackTrace();
            return null;
        }

        System.out.println("[RUN] === SecurityGuardSecurityBodyPlugin.onPluginLoaded ===");
        SecurityGuardSecurityBodyPlugin bodyPlugin = new SecurityGuardSecurityBodyPlugin();
        ISGPluginInfo bodyInfo = new PluginInfo("securitybody", "6.5.104", bodyPlugin);
        try {
            bodyPlugin.onPluginLoaded(context, router, bodyInfo, "sgsecuritybody", 0);
            System.out.println("[RUN] body plugin loaded");

            // 框架在插件 onPluginLoaded 之后会调 10102 把插件登记进 native（d.java:855）。
            // 不调这一步，主模块的 2xxxx 分发表永远是空的 → 9904。
            // 参数照 d.java: objArr = {插件名, str11, so 绝对路径}
            String bodySo = "/data/app/" + PKG + "-1/lib/arm64/libsgsecuritybodyso-6.5.104.so";
            Object reg = router.doCommand(10102, "sgsecuritybody", "6.5.104", bodySo);
            System.out.println("[RUN] register plugin(10102) -> " + SGHarness.describe(reg)
                    + "   slot=" + Long.toHexString(com.alibaba.wireless.security.framework.SGPluginExtras.slot));
        } catch (Throwable t) {
            System.out.println("[RUN] body plugin FAILED: " + t);
            t.printStackTrace();
            return null;
        }

        // securitybody 的 wua 生成前要先 initSecurityBody(appKey, packageName)（命令 20101）
        Object init = router.doCommand(20101, "12574478", PKG);
        System.out.println("[RUN] initSecurityBody(20101) -> " + SGHarness.describe(init));

        System.out.println("[RUN] === SecurityGuardMiddleTierPlugin.onPluginLoaded ===");
        mtPlugin = new com.alibaba.wireless.security.middletierplugin.SecurityGuardMiddleTierPlugin();
        ISGPluginInfo mtInfo = new PluginInfo("middletier", "6.5.90", mtPlugin);
        try {
            // d.java: 加载后续插件前先把 slot 清零，避免用上一个插件的分发函数去登记新插件
            com.alibaba.wireless.security.framework.SGPluginExtras.slot = 0L;
            mtPlugin.onPluginLoaded(context, router, mtInfo, "sgmiddletier", 0);
            System.out.println("[RUN] middletier JNI_OnLoad 后 slot=0x"
                    + Long.toHexString(com.alibaba.wireless.security.framework.SGPluginExtras.slot));
            String mtSo = "/data/app/" + PKG + "-1/lib/arm64/libsgmiddletierso-6.5.90.so";
            Object mreg = router.doCommand(10102, "sgmiddletier", "6.5.90", mtSo);
            System.out.println("[RUN] register middletier(10102) -> " + SGHarness.describe(mreg)
                    + "   slot=0x" + Long.toHexString(com.alibaba.wireless.security.framework.SGPluginExtras.slot));
        } catch (Throwable t) {
            System.out.println("[RUN] middletier FAILED: " + t);
            t.printStackTrace();
        }

        return bodyPlugin.getInterface(ISecurityBodyComponent.class);
    }

    /** 探针入口：初始化 + sign/wua 自检。 */
    public void run() {
        ISecurityBodyComponent body = initPlugins();
        if (body == null) {
            return;
        }

        // SG 的 onPluginLoaded 里有 schedule(new a(), 2L, TimeUnit.SECONDS) 的延迟初始化，
        // 等它跑完再签名（宿主 JVM 里这些线程是真的会跑的）。
        try {
            Thread.sleep(Long.getLong("sg.warmup.ms", 0L));
            System.out.println("[WARM] 等待 SG 异步初始化完成");
        } catch (InterruptedException ignored) {
        }

        // useWua=true 时，10401 内部会用到 wua。先预热一次 20102，让 SG 内部缓存就绪。
        try {
            String warm = body.getSecurityBodyDataEx(String.valueOf(System.currentTimeMillis()),
                    "12574478", "", null, 8, 0);
            System.out.println("[WARM] wua 预热 -> " + (warm == null ? "null" : warm.length() + " chars"));
        } catch (Throwable t) {
            System.out.println("[WARM] wua 预热异常: " + t);
        }

        // ---- 离线自证：signRequest(10401) 的输入是 mtopsdk 拼好的 INPUT 串 ----
        // mtopsdk 的 getMtopApiSign: requestType=7, paramMap={"INPUT": 21 段 & 拼接}
        try {
            com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent sig =
                    mainPlugin.getInterface(
                            com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent.class);
            String input = "hello&world&12574478";
            // requestType=7 的真实输入是 21 段 & 拼接（见 mtopsdk convertInnerBaseStrMap）
            StringBuilder big = new StringBuilder();
            for (int i = 0; i < 21; i++) {
                if (i > 0) big.append('&');
                big.append(i == 3 ? "12574478" : (i == 4 ? md5Hex("{}") : "x"));
            }
            String input21 = big.toString();
            System.out.println("[SIGN] input21 = " + input21);
            // authCode 之前一直是空串 —— 试 non-empty，native 很可能就是卡这个
            for (String ac : new String[]{"h5", "12574478", "default", "keep", ""}) {
                com.alibaba.wireless.security.open.SecurityGuardParamContext ctx =
                        new com.alibaba.wireless.security.open.SecurityGuardParamContext();
                ctx.appKey = "12574478";
                ctx.requestType = 2;
                ctx.paramMap.put("INPUT", input);
                try {
                    String sg = sig.signRequest(ctx, ac);
                    System.out.println("[SIGN] rt=2 authCode='" + ac + "' -> " + sg
                            + (md5Hex(input).equalsIgnoreCase(String.valueOf(sg)) ? "   ★== md5" : ""));
                } catch (Throwable t) {
                    System.out.println("[SIGN] rt=2 authCode='" + ac + "' 异常: " + t);
                }
            }
            for (int rt : new int[]{7, 3}) {
                com.alibaba.wireless.security.open.SecurityGuardParamContext ctx =
                        new com.alibaba.wireless.security.open.SecurityGuardParamContext();
                ctx.appKey = "12574478";
                ctx.requestType = rt;
                ctx.paramMap.put("INPUT", rt == 7 ? input21 : input);
                try {
                    String sg = sig.signRequest(ctx, "h5");
                    System.out.println("[SIGN] rt=" + rt + " authCode=h5 -> " + sg);
                } catch (Throwable t) {
                    System.out.println("[SIGN] rt=" + rt + " authCode=h5 异常: " + t);
                }
            }
        } catch (Throwable t) {
            System.out.println("[SIGN] 组件获取失败: " + t);
        }

        // ---- 直接调 70102（照 CSDN 文章的 9 参数格式，useWua=false）----
        if (router != null) {
            String api = "mtop.common.gettimestamp";
            String time = String.valueOf(System.currentTimeMillis());
            String md5v = md5Hex("{}");
            String devId = "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva";
            String data = "&&&12574478&" + md5v + "&" + time + "&" + api
                    + "&1.0&&600000@taobao_android_5.0.0&" + devId + "&&&&27&&&&&&&";
            System.out.println("[M70102] data=" + data);
            // 先直接 init middle tier（绕过 Java 接口），再取 factors
            try {
                Object i700 = router.doCommand(70101, "", 0);
                System.out.println("[M700] 70101 init -> " + SGHarness.describe(i700)
                        + "  err=" + com.alibaba.wireless.security.open.SecException.lastErrorCode);
                com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
            } catch (Throwable t) {
                System.out.println("[M700] 70101 异常: " + t);
            }
            try {
                Object r = router.doCommand(70102, "12574478", data, Boolean.FALSE, 0,
                        api, "pageId=&pageName=", null, null, null);
                System.out.println("[M70102] -> " + SGHarness.describe(r));
                if (r instanceof java.util.Map) {
                    for (Object e : ((java.util.Map<?, ?>) r).entrySet()) {
                        java.util.Map.Entry<?, ?> en = (java.util.Map.Entry<?, ?>) e;
                        String v = String.valueOf(en.getValue());
                        System.out.println("[M70102]   " + en.getKey() + " (" + v.length() + ") = "
                                + (v.length() > 80 ? v.substring(0, 80) : v));
                    }
                }
            } catch (Throwable t) {
                System.out.println("[M70102] 异常: " + t);
            }
        }

        // ---- getSecurityFactors：mtop 真正的反作弊头入口（一次给全部 4 个头）----
        if (mtPlugin != null) {
            try {
                com.alibaba.wireless.security.open.middletier.IUnifiedSecurityComponent us =
                        mtPlugin.getInterface(
                                com.alibaba.wireless.security.open.middletier.IUnifiedSecurityComponent.class);
                // 2403 = init 阶段被拒，先试几种 authCode
                for (String ac : new String[]{"", "h5", "12574478", "default"}) {
                    java.util.HashMap<String, Object> im = new java.util.HashMap<>();
                    im.put("authCode", ac);
                    im.put("flag", 0);
                    try {
                        us.init(im);
                        System.out.println("[UNIFIED] init(authCode='" + ac + "') ok");
                    } catch (Throwable t) {
                        System.out.println("[UNIFIED] init(authCode='" + ac + "') 失败: " + t);
                    }
                }

                StringBuilder big = new StringBuilder();
                for (int i = 0; i < 21; i++) {
                    if (i > 0) big.append('&');
                    big.append(i == 3 ? "12574478" : (i == 4 ? md5Hex("{}") : "x"));
                }
                java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                m.put("appkey", "12574478");
                m.put("data", big.toString());
                m.put("useWua", Boolean.TRUE);
                m.put("env", 0);
                m.put("api", "mtop.tmall.campus.member.app.user.get");
                m.put("authCode", "");
                m.put("requestId", "req-" + System.currentTimeMillis());
                m.put("flag", 0);
                java.util.HashMap<String, String> factors = us.getSecurityFactors(m);
                if (factors == null) {
                    System.out.println("[UNIFIED] null   [err="
                            + com.alibaba.wireless.security.open.SecException.lastErrorCode + "]");
                } else {
                    System.out.println("[UNIFIED] keys=" + factors.keySet());
                    for (java.util.Map.Entry<String, String> en : factors.entrySet()) {
                        String v = en.getValue();
                        System.out.println("[UNIFIED] " + en.getKey() + " (" + (v == null ? 0 : v.length())
                                + ") = " + (v == null ? "null" : (v.length() > 100 ? v.substring(0, 100) : v)));
                    }
                }
            } catch (Throwable t) {
                System.out.println("[UNIFIED] 异常: " + t);
            }
        }

        // 直接打 10401：对比第 5 个布尔参数 true/false（C0030 传 true，C0042 传 false）
        if (router != null) {
            // 按 mtopsdk convertInnerBaseStrMap 的真实顺序拼 22 段（deviceId 用真机的）
            String tReal = String.valueOf(System.currentTimeMillis());
            String[] seg = new String[]{
                    "", "", "", "12574478", md5Hex("{}"), tReal,
                    "mtop.tmall.campus.member.app.user.get", "1.0", "", "600000@taobao_android_5.0.0",
                    "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva", "", "", "", "", "", "", "", "", "", "", ""
            };
            String realInput22 = String.join("&", seg);
            System.out.println("[REAL] input22 = " + realInput22);
            java.util.HashMap<String, String> m2 = new java.util.HashMap<>();
            m2.put("INPUT", realInput22);
            for (int rt : new int[]{7, 2}) {
                try {
                    Object r = router.doCommand(10401, m2, "12574478", rt, "", true);
                    System.out.println("[REAL10401] rt=" + rt + " -> " + SGHarness.describe(r));
                } catch (Throwable tt) {
                    System.out.println("[REAL10401] rt=" + rt + " 异常: " + tt);
                }
            }

            java.util.HashMap<String, String> m = new java.util.HashMap<>();
            m.put("INPUT", "hello&world&12574478");
            for (boolean flag : new boolean[]{false, true}) {
                for (int rt : new int[]{2, 7}) {
                    try {
                        Object r = router.doCommand(10401, m, "12574478", rt, "", flag);
                        System.out.println("[D10401] rt=" + rt + " bool=" + flag + " -> "
                                + SGHarness.describe(r));
                    } catch (Throwable t) {
                        System.out.println("[D10401] rt=" + rt + " bool=" + flag + " 异常: " + t);
                    }
                }
            }
        }

        String ts = String.valueOf(System.currentTimeMillis());
        // 同一参数连打 3 次：判断是活计算还是缓存/降级
        for (int k = 0; k < 3; k++) {
            try {
                String w = body.getSecurityBodyDataEx(ts, "12574478", "", null, 8, 0);
                System.out.println("[WUA] rep" + k + " -> " + (w == null ? "null" : w.length() + " chars: " + w));
            } catch (Throwable t) {
                System.out.println("[WUA] rep" + k + " 异常: " + t);
            }
        }
        for (int flag : new int[]{8, 4, 1}) {
            try {
                String wua = body.getSecurityBodyDataEx(ts, "12574478", "", null, flag, 0);
                System.out.println("[WUA] flag=" + flag + " -> "
                        + (wua == null ? "null" : wua.length() + " chars: "
                        + wua.substring(0, Math.min(120, wua.length()))));
            } catch (Throwable t) {
                System.out.println("[WUA] flag=" + flag + " 异常: " + t);
            }
        }
    }

    /** 最小 ISGPluginInfo 实现：SG 只在少数地方读插件名/版本/入口。 */
    static final class PluginInfo implements ISGPluginInfo {
        private final String name;
        private final String version;
        private final ISecurityGuardPlugin entry;

        PluginInfo(String name, String version, ISecurityGuardPlugin entry) {
            this.name = name;
            this.version = version;
            this.entry = entry;
        }

        @Override
        public android.content.res.AssetManager getAssetManager() {
            return new android.content.res.AssetManager();
        }

        @Override
        public ClassLoader getClassLoader() {
            return SGRunner.class.getClassLoader();
        }

        @Override
        public android.content.pm.PackageInfo getPackageInfo() {
            return android.content.pm.PackageInfo.of(PKG);
        }

        @Override
        public String getPluginName() {
            return name;
        }

        @Override
        public String getPluginPath() {
            return new File(LIB_DIR, "lib".equals(name) ? "libsgmain.so"
                    : "lib" + name + ".so").getAbsolutePath();
        }

        @Override
        public ISecurityGuardPlugin getSGPluginEntry() {
            return entry;
        }

        @Override
        public ISGPluginManager getSGPluginManager() {
            return null;
        }

        @Override
        public String getVersion() {
            return version;
        }
    }

    public void destroy() throws IOException {
        emulator.close();
    }
}
