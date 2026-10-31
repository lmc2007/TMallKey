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

    public void run() {
        IRouterComponent router = new C0001();

        System.out.println("[RUN] === SecurityGuardMainPlugin.onPluginLoaded ===");
        SecurityGuardMainPlugin mainPlugin = new SecurityGuardMainPlugin();
        ISGPluginInfo mainInfo = new PluginInfo("sgmain", "6.5.97", mainPlugin);
        try {
            mainPlugin.onPluginLoaded(context, router, mainInfo, "sgmain", 0,
                    "12574478", "5.0.0", "/data/data/" + PKG + "/files");
            System.out.println("[RUN] main plugin loaded");
        } catch (Throwable t) {
            System.out.println("[RUN] main plugin FAILED: " + t);
            t.printStackTrace();
            return;
        }

        System.out.println("[RUN] === SecurityGuardSecurityBodyPlugin.onPluginLoaded ===");
        SecurityGuardSecurityBodyPlugin bodyPlugin = new SecurityGuardSecurityBodyPlugin();
        ISGPluginInfo bodyInfo = new PluginInfo("securitybody", "6.5.104", bodyPlugin);
        try {
            bodyPlugin.onPluginLoaded(context, router, bodyInfo, "sgsecuritybody", 0);
            System.out.println("[RUN] body plugin loaded");
        } catch (Throwable t) {
            System.out.println("[RUN] body plugin FAILED: " + t);
            t.printStackTrace();
            return;
        }

        // securitybody 的 wua 生成前要先 initSecurityBody(appKey, packageName)（命令 20101）
        Object init = router.doCommand(20101, "12574478", PKG);
        System.out.println("[RUN] initSecurityBody(20101) -> " + SGHarness.describe(init));

        ISecurityBodyComponent body = bodyPlugin.getInterface(ISecurityBodyComponent.class);
        System.out.println("[RUN] ISecurityBodyComponent = " + body);
        if (body == null) {
            return;
        }

        String ts = String.valueOf(System.currentTimeMillis());
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
