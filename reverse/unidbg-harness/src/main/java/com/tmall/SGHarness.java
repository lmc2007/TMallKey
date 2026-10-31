package com.tmall;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.wrapper.DvmBoolean;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.linux.android.dvm.wrapper.DvmLong;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.memory.Memory;

import java.io.File;
import java.io.IOException;

/**
 * SecurityGuard native harness — 阶段 2/3：加载 libsgmainso、JNI_OnLoad、initialize(10101)。
 *
 * 全程把官方 SO 当黑盒：不逆算法，只喂参数取返回值。
 */
public class SGHarness {

    /** 资产目录：默认从 CWD 往上找 tools/unidbg-tmall，也可用 -Dsg.libdir 指定 */
    public static final String LIB_DIR = resolveDir("sg.libdir", "tools/unidbg-tmall/libs");
    public static final String DATA_DIR = resolveDir("sg.datadir", "tools/unidbg-tmall/sgdata");
    public static final String PKG = "com.tmall.campus";
    public static final String SGMAIN = "libsgmainso-6.5.97.so";
    public static final String JNI_CLASS = "com/taobao/wireless/security/adapter/JNICLibrary";
    public static final String DO_COMMAND_SIG =
            "doCommandNative(I[Ljava/lang/Object;)Ljava/lang/Object;";

    private final AndroidEmulator emulator;
    private final VM vm;
    private final SGJni jni;
    private final Module module;
    private final DvmClass jniLibrary;
    private final DvmObject<?> context;

    public SGHarness(boolean verbose) {
        emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName(PKG)
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        vm = emulator.createDalvikVM();
        vm.setVerbose(verbose);

        // 关掉 ProxyClassFactory 的反射路径，所有 Java 调用都走我们的 SGJni
        jni = new SGJni(emulator, vm, PKG, false);
        vm.setJni(jni);
        vm.setDvmClassFactory(new PlainClassFactory());

        installIoResolver();

        File so = new File(LIB_DIR, SGMAIN);
        if (!so.exists()) {
            throw new IllegalStateException("missing so: " + so.getAbsolutePath());
        }
        DalvikModule dm = vm.loadLibrary(so, true);
        module = dm.getModule();
        log("loaded " + module.name + " base=0x" + Long.toHexString(module.base));

        jniLibrary = vm.resolveClass(JNI_CLASS);
        context = jni.newContext();

        dm.callJNI_OnLoad(emulator);
        log("JNI_OnLoad executed");

    }

    /** 插件模块加载：真机上是 initialize 之后由框架逐个 loadLibrary + onPluginLoaded。 */
    public void loadPluginModules(String... extras) {
        for (String extra : extras) {
            File f = new File(LIB_DIR, extra);
            if (!f.exists()) {
                log("skip missing " + extra);
                continue;
            }
            try {
                DalvikModule m = vm.loadLibrary(f, true);
                m.callJNI_OnLoad(emulator);
                log("loaded extra " + m.getModule().name + " base=0x" + Long.toHexString(m.getModule().base));
            } catch (Throwable t) {
                log("load extra failed " + extra + ": " + t);
            }
        }
    }

    /**
     * 把 /data/data/<pkg>/ 下的文件读写重定向到本地 sgdata 目录，
     * 这样 SecurityGuard 初始化时读的 yw_1222.jpg 等资产可以用提取出来的真文件。
     */
    private void installIoResolver() {
        File dataRoot = new File(DATA_DIR).getAbsoluteFile();
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            log("cannot create data dir " + dataRoot);
        }
        emulator.getSyscallHandler().addIOResolver(new IOResolver<AndroidFileIO>() {
            @Override
            public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emu, String pathname, int oflags) {
                String prefix = "/data/data/" + PKG + "/";
                if (!pathname.startsWith(prefix)) {
                    return null;
                }
                String rel = pathname.substring(prefix.length());
                File host = new File(dataRoot, rel);
                boolean wantDir = (oflags & 0x10000) != 0; // O_DIRECTORY
                if (Boolean.getBoolean("sg.iolog")) {
                    System.out.println("[IO] open " + pathname + " oflags=0x"
                            + Integer.toHexString(oflags) + " host=" + (host.isDirectory() ? "dir" : host.isFile() ? "file" : "none"));
                }
                if (host.isDirectory() && (wantDir || true)) {
                    return FileResult.success(new DirectoryFileIO(oflags, pathname, host));
                }
                if (!host.exists() && (oflags & 3) != 0) {
                    // 写模式（含 O_CREAT）：在本地建文件，让 native 的缓存能落盘
                    File parent = host.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try {
                        if (host.createNewFile()) {
                            return FileResult.success(new SimpleFileIO(oflags, host, pathname));
                        }
                    } catch (java.io.IOException e) {
                        System.out.println("[IO] create failed " + host + ": " + e.getMessage());
                    }
                }
                if (host.isFile() && wantDir) {
                    // 目录语义但实际是文件：返回 null 让 open 失败，避免 getdents64 崩在 SimpleFileIO 上
                    return null;
                }
                if (host.isFile()) {
                    return FileResult.success(new SimpleFileIO(oflags, host, pathname));
                }
                if ((oflags & 3) != 2) { // 非写模式才记录缺口
                    System.out.println("[IO-MISS] open " + pathname
                            + " (oflags=0x" + Integer.toHexString(oflags) + ")");
                }
                return null;
            }
        });
    }

    /**
     * 直接调用 native 入口 doCommandNative(int, Object...)。
     * unidbg 不接受裸 Object[]，必须转成 DVM 的 ArrayObject。
     */
    public Object doCommand(int code, Object... args) {
        DvmObject<?>[] params = new DvmObject<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            params[i] = toDvm(args[i]);
        }
        return jniLibrary.callStaticJniMethodObject(emulator, DO_COMMAND_SIG, code,
                new ArrayObject(params));
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
        throw new IllegalArgumentException("unsupported arg type: " + o.getClass());
    }

    /** SecurityGuard initialize：doCommand(10101, {context, int, str, str, str}) */
    public int initialize(int env, String appKey, String appVersion, String cachePath) {
        Object ret = doCommand(10101, context, jni.newInt(env),
                jni.newString(appKey), jni.newString(appVersion), jni.newString(cachePath));
        log("initialize(10101) -> " + describe(ret));
        return ret instanceof Number ? ((Number) ret).intValue() : -1;
    }

    public AndroidEmulator getEmulator() {
        return emulator;
    }

    public VM getVm() {
        return vm;
    }

    public Module getModule() {
        return module;
    }

    public DvmClass getJniLibrary() {
        return jniLibrary;
    }

    public SGJni getJni() {
        return jni;
    }

    public void destroy() throws IOException {
        emulator.close();
    }

    public static String describe(Object o) {
        if (o instanceof DvmObject) {
            DvmObject<?> d = (DvmObject<?>) o;
            return d.getClass().getSimpleName() + "(" + d.getObjectType().getName() + ")=" + d.getValue();
        }
        return String.valueOf(o);
    }

    private static String resolveDir(String prop, String rel) {
        String explicit = System.getProperty(prop);
        if (explicit != null) {
            return explicit;
        }
        for (String prefix : new String[]{"", "../", "../../", "../../../"}) {
            File f = new File(prefix + rel);
            if (f.exists()) {
                return f.getPath();
            }
        }
        return rel;
    }

    private static void log(String s) {
        System.out.println("[SG] " + s);
    }

    public static void main(String[] args) throws Exception {
        boolean verbose = false;
        boolean doInit = true;
        for (String a : args) {
            if ("--verbose".equals(a)) verbose = true;
            if ("--no-init".equals(a)) doInit = false;
        }
        SGHarness harness = new SGHarness(verbose);
        if (doInit) {
            harness.initialize(0, "12574478", "5.0.0", "/data/data/" + PKG + "/files");
            harness.loadPluginModules("libsgcore.so", "libsgmiddletierso-6.5.90.so",
                    "libsgsecuritybodyso-6.5.104.so");
            // 试打 securitybody 的 getSecurityBodyDataEx（20102）
            // Java: doCommand(20102, {arg0, appKey, flag, authCode, extString, env})
            // 10401 = signRequest(map, appKey, requestType, secret, true)
            java.util.Map<String, String> paramMap = new java.util.LinkedHashMap<>();
            paramMap.put("INPUT", "test_input");
            DvmObject<?> mapObj = harness.getVm().resolveClass("java/util/HashMap").newObject(paramMap);
            Object sign = harness.doCommand(10401, mapObj, "12574478", 3, "h5", true);
            System.out.println("[PROBE] 10401 sign -> " + describe(sign));

            // 20101 = initSecurityBody(appKey, packageName)，不先调它 20102 会报 9904
            for (String bodyKey : new String[]{"12574478"}) {
                Object init = harness.doCommand(20101, bodyKey, PKG);
                System.out.println("[PROBE] 20101 initSecurityBody(" + bodyKey + ") -> " + describe(init));
            }
            String ts = String.valueOf(System.currentTimeMillis());
            for (int flag : new int[]{8, 4, 1}) {
                Object r = harness.doCommand(20102, ts, "12574478", flag, "h5", "", 0);
                System.out.println("[PROBE] 20102 flag=" + flag + " ts=" + ts + " -> " + describe(r));
            }
        }
        harness.destroy();
        log("done");
    }
}
