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
    private final AndroidEmulator emulator;
    private final VM vm;
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
        System.out.println("[RUN] loaded " + module.name + " base=0x" + Long.toHexString(module.base));

        jniLibrary = vm.resolveClass(SGHarness.JNI_CLASS);
        dm.callJNI_OnLoad(emulator);
        System.out.println("[RUN] JNI_OnLoad ok");

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
    private Object dispatch(int code, Object[] args) {
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
        IRouterComponent router = new C0001();

        System.out.println("[RUN] === SecurityGuardMainPlugin.onPluginLoaded ===");
        mainPlugin = new SecurityGuardMainPlugin();
        ISGPluginInfo mainInfo = new PluginInfo("sgmain", "6.5.97", mainPlugin);
        try {
            mainPlugin.onPluginLoaded(context, router, mainInfo, "sgmain", 0,
                    "12574478", "5.0.0", "/data/data/" + PKG + "/files");
            String mainSo = new java.io.File(LIB_DIR, "libsgmainso-6.5.97.so").getAbsolutePath();
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
            String bodySo = new java.io.File(LIB_DIR, "libsgsecuritybodyso-6.5.104.so").getAbsolutePath();
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
            String mtSo = new java.io.File(LIB_DIR, "libsgmiddletierso-6.5.90.so").getAbsolutePath();
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
            for (int rt : new int[]{2, 7, 9, 3, 10}) {
                com.alibaba.wireless.security.open.SecurityGuardParamContext ctx =
                        new com.alibaba.wireless.security.open.SecurityGuardParamContext();
                ctx.appKey = "12574478";
                ctx.requestType = rt;
                ctx.paramMap.put("INPUT", rt == 7 ? input21 : input);
                try {
                    String sg = sig.signRequest(ctx, "");
                    String md5 = md5Hex(rt == 7 ? input21 : input);
                    System.out.println("[SIGN] rt=" + rt + " -> " + sg
                            + "   md5(INPUT)=" + md5
                            + (md5.equalsIgnoreCase(String.valueOf(sg)) ? "   ★一致" : ""));
                } catch (Throwable t) {
                    System.out.println("[SIGN] rt=" + rt + " 异常: " + t);
                }
            }
        } catch (Throwable t) {
            System.out.println("[SIGN] 组件获取失败: " + t);
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
