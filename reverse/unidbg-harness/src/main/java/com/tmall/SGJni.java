package com.tmall;

import com.github.unidbg.Emulator;
import com.github.unidbg.linux.android.dvm.AbstractJni;
import com.github.unidbg.linux.android.dvm.BaseVM;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmObject;
import com.github.unidbg.linux.android.dvm.StringObject;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.VaList;
import com.github.unidbg.linux.android.dvm.VarArg;
import com.github.unidbg.linux.android.dvm.api.ClassLoader;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.wrapper.DvmBoolean;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.linux.android.dvm.wrapper.DvmLong;

/**
 * SecurityGuard 的 JNI 垫片。
 *
 * native 侧（libsgmainso）会大量回调 Java 层：Context、SPUtility2、DeviceInfoCapturer…
 * 真机上这些 Java 类由 SecurityGuard 的 plugin dex 提供，unidbg 里没有，只能自己实现。
 *
 * 注意：unidbg 有 VaList / VarArg 两条回调路径（ProxyJni 走 VarArg），两边都要实现。
 * 未实现的调用统一打 [JNI-MISS] 并返回默认值 —— 能继续跑，同时暴露缺口。
 */
public class SGJni extends AbstractJni {

    private final Emulator<?> emulator;
    private final VM vm;
    private final String packageName;
    /** 静态字段必须真的存下来：SGPluginExtras.slot 存的是插件 native 分发函数指针 */
    private final java.util.Map<String, Long> staticLongFields = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> staticIntFields = new java.util.HashMap<>();
    private final java.util.Map<String, DvmObject<?>> staticObjectFields = new java.util.HashMap<>();

    public SGJni(Emulator<?> emulator, VM vm, String packageName, boolean strict) {
        this.emulator = emulator;
        this.vm = vm;
        this.packageName = packageName;
    }

    private void miss(String kind, String signature, Object def) {
        System.out.println("[JNI-MISS] " + kind + " " + signature + " -> " + def);
    }

    // ================= object =================

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        return staticObject(signature, vaList);
    }

    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        return staticObject(signature, varArg);
    }

    private DvmObject<?> staticObject(String signature, VarArg args) {
        switch (signature) {
            // SecurityGuard 插件体系：native 需要 ClassLoader 去 loadClass
            case "com/alibaba/wireless/security/mainplugin/SecurityGuardMainPlugin->getMainPluginClassLoader()Ljava/lang/ClassLoader;":
                return new ClassLoader(vm, signature);
            case "com/alibaba/wireless/security/mainplugin/SecurityGuardMainPlugin->getContext()Landroid/content/Context;":
                return context();
            case "com/alibaba/wireless/security/adapter/common/SPUtility2->readFromSPUnified(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;":
            case "com/alibaba/wireless/security/adapter/common/SPUtility2->readSS(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;":
            case "com/alibaba/wireless/security/adapter/common/SPUtility2->read(Ljava/lang/String;)Ljava/lang/String;":
            case "com/taobao/wireless/security/adapter/common/HttpUtil->sendSyncHttpGetRequestBridge(Ljava/lang/String;)Ljava/lang/String;":
            case "com/taobao/wireless/security/adapter/common/HttpUtil->sendSyncHttpPostRequestBridge(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)Ljava/lang/String;":
            case "com/taobao/wireless/security/adapter/datacollection/DeviceInfoCapturer->doCommandForString(I)Ljava/lang/String;":
                return new StringObject(vm, "");
            case "com/alibaba/wireless/security/securitybody/SecurityGuardSecurityBodyPlugin->getPluginClassLoader()Ljava/lang/ClassLoader;":
            case "com/alibaba/wireless/security/securitybody/InvocationHandlerAdapter->getClassLoader()Ljava/lang/ClassLoader;":
                return new ClassLoader(vm, signature);
            case "com/taobao/dp/util/CallbackHelper->getInstance()Lcom/taobao/dp/util/CallbackHelper;":
                return vm.resolveClass("com/taobao/dp/util/CallbackHelper").newObject(null);
            case "com/alibaba/wireless/security/open/edgecomputing/ProxyUtil->getHandler()Landroid/os/Handler;":
                return vm.resolveClass("android/os/Handler").newObject(null);
            case "com/alibaba/wireless/security/open/edgecomputing/ProxyUtil->getProxyInstance(Ljava/lang/String;Ljava/lang/Object;I)Ljava/lang/Object;":
                return args.getObjectArg(1);
            default:
                miss("callStaticObjectMethod", signature, "null");
                return null;
        }
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        return objectMethod(dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        return objectMethod(dvmObject, signature, varArg);
    }

    private DvmObject<?> objectMethod(DvmObject<?> dvmObject, String signature, VarArg args) {
        switch (signature) {
            case "android/content/Context->getApplicationContext()Landroid/content/Context;":
                return context();
            case "android/content/Context->getFilesDir()Ljava/io/File;":
                return fileObj("/data/data/" + packageName + "/files");
            case "android/content/Context->getCacheDir()Ljava/io/File;":
                return fileObj("/data/data/" + packageName + "/cache");
            case "android/content/Context->getCodeCacheDir()Ljava/io/File;":
                return fileObj("/data/data/" + packageName + "/code_cache");
            case "android/content/Context->getDir(Ljava/lang/String;I)Ljava/io/File;":
                return fileObj("/data/data/" + packageName + "/app_" + args.getObjectArg(0).getValue());
            case "android/content/Context->getDatabasePath(Ljava/lang/String;)Ljava/io/File;":
                return fileObj("/data/data/" + packageName + "/databases/" + args.getObjectArg(0).getValue());
            case "android/content/Context->getApplicationInfo()Landroid/content/pm/ApplicationInfo;":
                return vm.resolveClass("android/content/pm/ApplicationInfo").newObject(null);
            case "android/content/Context->getPackageResourcePath()Ljava/lang/String;":
                return new StringObject(vm, "/data/app/" + packageName + "-1/base.apk");
            case "android/content/Context->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;":
                return vm.resolveClass("android/content/SharedPreferences").newObject(null);
            case "android/content/Context->getAssets()Landroid/content/res/AssetManager;":
                return vm.resolveClass("android/content/res/AssetManager").newObject(null);
            case "android/content/Context->getClassLoader()Ljava/lang/ClassLoader;":
                return new ClassLoader(vm, signature);
            case "java/lang/ClassLoader->loadClass(Ljava/lang/String;)Ljava/lang/Class;": {
                String name = args.getObjectArg(0).getValue().toString();
                return vm.resolveClass(name.replace('.', '/'));
            }
            case "java/lang/Class->getName()Ljava/lang/String;":
                return new StringObject(vm, dvmObject.getObjectType().getName().replace('/', '.'));
            case "java/util/HashMap->get(Ljava/lang/Object;)Ljava/lang/Object;":
            case "java/util/Map->get(Ljava/lang/Object;)Ljava/lang/Object;": {
                Object v = hashGet(dvmObject, args.getObjectArg(0));
                return v == null ? null : new StringObject(vm, String.valueOf(v));
            }
            case "android/content/Context->getPackageName()Ljava/lang/String;":
                return new StringObject(vm, packageName);
            case "android/content/Context->getPackageCodePath()Ljava/lang/String;":
                return new StringObject(vm, "/data/app/" + packageName + "-1/base.apk");
            case "android/content/Context->getOpPackageName()Ljava/lang/String;":
                return new StringObject(vm, packageName);
            case "java/io/File->getAbsolutePath()Ljava/lang/String;":
            case "java/io/File->getPath()Ljava/lang/String;":
            case "java/lang/String->toString()Ljava/lang/String;":
                return new StringObject(vm, String.valueOf(dvmObject.getValue()));
            default:
                miss("callObjectMethod(" + dvmObject.getObjectType().getName() + ")", signature, "null");
                return null;
        }
    }

    // ================= int =================

    @Override
    public int callStaticIntMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        return staticInt(signature);
    }

    @Override
    public int callStaticIntMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        return staticInt(signature);
    }

    private int staticInt(String signature) {
        switch (signature) {
            case "com/taobao/wireless/security/adapter/common/SPUtility2->saveToFileUnifiedForNative(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)I":
            case "com/alibaba/wireless/security/adapter/datareport/DataReportJniBridge->accsAvaiableBridge()I":
            case "com/alibaba/wireless/security/adapter/datareport/DataReportJniBridge->registerAccsListnerBridge()I":
                return 0;
            default:
                miss("callStaticIntMethod", signature, 0);
                return 0;
        }
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        return intMethod(dvmObject, signature);
    }

    @Override
    public int callIntMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        return intMethod(dvmObject, signature);
    }

    @SuppressWarnings("unchecked")
    private static Object hashGet(DvmObject<?> map, DvmObject<?> key) {
        if (map.getValue() instanceof java.util.Map && key != null) {
            return ((java.util.Map<Object, Object>) map.getValue()).get(key.getValue());
        }
        return null;
    }

    private int intMethod(DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "java/util/HashMap->size()I":
            case "java/util/Map->size()I": {
                Object v = dvmObject.getValue();
                return v instanceof java.util.Map ? ((java.util.Map<?, ?>) v).size() : 0;
            }
            case "java/lang/Integer->intValue()I":
            case "java/lang/Number->intValue()I":
                return unboxInt(dvmObject);
            case "android/content/Context->checkSelfPermission(Ljava/lang/String;)I":
            case "android/content/Context->checkCallingOrSelfPermission(Ljava/lang/String;)I":
                return 0; // PERMISSION_GRANTED
            default:
                miss("callIntMethod", signature, 0);
                return 0;
        }
    }

    // ================= boolean =================

    @Override
    public boolean callStaticBooleanMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        return staticBoolean(signature);
    }

    @Override
    public boolean callStaticBooleanMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        return staticBoolean(signature);
    }

    private boolean staticBoolean(String signature) {
        switch (signature) {
            case "com/alibaba/wireless/security/framework/ApmMonitorAdapter->isEnableFullTrackRecord()Z":
            case "com/alibaba/wireless/security/framework/ApmMonitorAdapter->isForeground()Z":
                return false;
            case "com/taobao/wireless/security/adapter/common/SPUtility2->removeFromSPUnified(Ljava/lang/String;Ljava/lang/String;Z)Z":
                return true;
            default:
                miss("callStaticBooleanMethod", signature, false);
                return false;
        }
    }

    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        return booleanMethod(dvmObject, signature);
    }

    @Override
    public boolean callBooleanMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        return booleanMethod(dvmObject, signature);
    }

    private boolean booleanMethod(DvmObject<?> dvmObject, String signature) {
        if ("java/lang/Boolean->booleanValue()Z".equals(signature)) {
            Object v = dvmObject.getValue();
            return v instanceof Boolean && (Boolean) v;
        }
        miss("callBooleanMethod(" + dvmObject.getObjectType().getName() + ")", signature, false);
        return false;
    }

    // ================= long =================

    @Override
    public long callStaticLongMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        return staticLong(signature);
    }

    @Override
    public long callStaticLongMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        return staticLong(signature);
    }

    private long staticLong(String signature) {
        switch (signature) {
            case "android/os/SystemClock->elapsedRealtime()J":
                return System.currentTimeMillis();
            default:
                miss("callStaticLongMethod", signature, 0L);
                return 0L;
        }
    }

    @Override
    public long callLongMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        return longMethod(dvmObject, signature);
    }

    @Override
    public long callLongMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        return longMethod(dvmObject, signature);
    }

    private long longMethod(DvmObject<?> dvmObject, String signature) {
        if ("java/lang/Long->longValue()J".equals(signature) || "java/lang/Number->longValue()J".equals(signature)) {
            Object v = dvmObject.getValue();
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        }
        miss("callLongMethod(" + dvmObject.getObjectType().getName() + ")", signature, 0L);
        return 0L;
    }

    /** native 拿到的往往是包装好的 Integer/Long，必须按值拆箱，否则时间戳/长度全是 0。 */
    private static int unboxInt(DvmObject<?> o) {
        Object v = o == null ? null : o.getValue();
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    @Override
    public DvmObject<?> newObjectV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        return newObject0(dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> newObject(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        return newObject0(dvmClass, signature, varArg);
    }

    private DvmObject<?> newObject0(DvmClass dvmClass, String signature, VarArg args) {
        // native 用 NewObject 造包装类（比如返回码 Integer），值必须带过去，否则后面拆箱全是 null/0
        switch (signature) {
            case "java/lang/Integer-><init>(I)V":
                return DvmInteger.valueOf(vm, args.getIntArg(0));
            case "java/lang/Long-><init>(J)V":
                return DvmLong.valueOf(vm, args.getLongArg(0));
            case "java/lang/Boolean-><init>(Z)V":
                return DvmBoolean.valueOf(vm, args.getIntArg(0) != 0);
            case "java/lang/String-><init>(Ljava/lang/String;)V":
                return new StringObject(vm, String.valueOf(args.getObjectArg(0).getValue()));
            case "java/lang/StringBuilder-><init>()V":
            case "java/lang/Object-><init>()V":
                return dvmClass.newObject(null);
            default:
                break;
        }
        if (signature.startsWith("com/alibaba/wireless/security/open/SecException-><init>")) {
            Object m = args.getObjectArg(0);
            String msg = m instanceof DvmObject ? String.valueOf(((DvmObject<?>) m).getValue()) : String.valueOf(m);
            int code = safeInt(args, 1);
            System.out.println("[SG] SecException(" + code + "): " + msg);
        }
        return dvmClass.newObject(null);
    }

    private static int safeInt(VarArg args, int index) {
        try {
            return args.getIntArg(index);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ================= void =================

    @Override
    public void callStaticVoidMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        staticVoid(signature);
    }

    @Override
    public void callStaticVoidMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        staticVoid(signature);
    }

    private void staticVoid(String signature) {
        switch (signature) {
            case "com/alibaba/wireless/security/adapter/common/SPUtility2->write(Ljava/lang/String;Ljava/lang/String;)V":
            case "com/alibaba/wireless/security/open/edgecomputing/ECMiscInfo->updateAppVersion(Ljava/lang/String;)V":
            case "com/alibaba/wireless/security/open/edgecomputing/ECMiscInfo->updateAppFirstRunState()V":
            case "com/alibaba/wireless/security/open/edgecomputing/ECMiscInfo->registerAppLifeCyCleCallBack()V":
                return;
            default:
                miss("callStaticVoidMethod", signature, "void");
        }
    }

    @Override
    public void callVoidMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        miss("callVoidMethod(" + dvmObject.getObjectType().getName() + ")", signature, "void");
    }

    @Override
    public void callVoidMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        miss("callVoidMethod(" + dvmObject.getObjectType().getName() + ")", signature, "void");
    }

    // ================= fields =================

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "com/alibaba/wireless/security/framework/SGPluginExtras->slot:J":
                return null; // 走 long，不该走这里
            case "android/os/Build->MANUFACTURER:Ljava/lang/String;":
                return new StringObject(this.vm, "Xiaomi");
            case "android/os/Build->BRAND:Ljava/lang/String;":
                return new StringObject(this.vm, "Xiaomi");
            case "android/os/Build->MODEL:Ljava/lang/String;":
                return new StringObject(this.vm, "M2102J2SC");
            case "android/os/Build->DEVICE:Ljava/lang/String;":
                return new StringObject(this.vm, "thyme");
            case "android/os/Build->PRODUCT:Ljava/lang/String;":
                return new StringObject(this.vm, "thyme");
            case "android/os/Build->HARDWARE:Ljava/lang/String;":
                return new StringObject(this.vm, "qcom");
            case "android/os/Build$VERSION->RELEASE:Ljava/lang/String;":
                return new StringObject(this.vm, "11");
            case "android/os/Build$VERSION->SDK:Ljava/lang/String;":
                return new StringObject(this.vm, "30");
            default:
                {
                    DvmObject<?> stored = staticObjectFields.get(signature);
                    if (stored != null) {
                        return stored;
                    }
                }
                miss("getStaticObjectField", signature, "null");
                return null;
        }
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/content/pm/ApplicationInfo->sourceDir:Ljava/lang/String;":
            case "android/content/pm/ApplicationInfo->publicSourceDir:Ljava/lang/String;":
                return new StringObject(this.vm, "/data/app/" + packageName + "-1/base.apk");
            case "android/content/pm/ApplicationInfo->dataDir:Ljava/lang/String;":
                return new StringObject(this.vm, "/data/data/" + packageName);
            case "android/content/pm/ApplicationInfo->nativeLibraryDir:Ljava/lang/String;":
                return new StringObject(this.vm, "/data/app/" + packageName + "-1/lib/arm64");
            case "android/content/pm/ApplicationInfo->packageName:Ljava/lang/String;":
                return new StringObject(this.vm, packageName);
            case "android/content/pm/ApplicationInfo->processName:Ljava/lang/String;":
                return new StringObject(this.vm, packageName);
            case "android/content/pm/ApplicationInfo->className:Ljava/lang/String;":
                return new StringObject(this.vm, "");
            case "android/content/pm/ApplicationInfo->splitSourceDirs:[Ljava/lang/String;":
                return null;
            default:
                miss("getObjectField(" + dvmObject.getObjectType().getName() + ")", signature, "null");
                return null;
        }
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/content/pm/ApplicationInfo->flags:I":
            case "android/content/pm/ApplicationInfo->targetSdkVersion:I":
                return 0;
            default:
                miss("getIntField", signature, 0);
                return 0;
        }
    }

    @Override
    public long getStaticLongField(BaseVM vm, DvmClass dvmClass, String signature) {
        Long v = staticLongFields.get(signature);
        if (v != null) {
            return v;
        }
        miss("getStaticLongField", signature, 0L);
        return 0L;
    }

    // native 会写静态字段（SGPluginExtras.slot 等），写不进去就抛 UOE
    @Override
    public void setStaticLongField(BaseVM vm, DvmClass dvmClass, String signature, long value) {
        staticLongFields.put(signature, value);
        System.out.println("[JNI] setStaticLongField " + signature + " = 0x" + Long.toHexString(value));
    }

    @Override
    public void setStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature, DvmObject<?> value) {
        staticObjectFields.put(signature, value);
        System.out.println("[JNI] setStaticObjectField " + signature + " = " + value);
    }

    @Override
    public void setStaticIntField(BaseVM vm, DvmClass dvmClass, String signature, int value) {
        staticIntFields.put(signature, value);
        System.out.println("[JNI] setStaticIntField " + signature + " = " + value);
    }

    @Override
    public void setObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature, DvmObject<?> value) {
        System.out.println("[JNI] setObjectField " + signature + " = " + value);
    }

    @Override
    public void setIntField(BaseVM vm, DvmObject<?> dvmObject, String signature, int value) {
        System.out.println("[JNI] setIntField " + signature + " = " + value);
    }

    @Override
    public void setLongField(BaseVM vm, DvmObject<?> dvmObject, String signature, long value) {
        System.out.println("[JNI] setLongField " + signature + " = " + value);
    }

    @Override
    public void setBooleanField(BaseVM vm, DvmObject<?> dvmObject, String signature, boolean value) {
        System.out.println("[JNI] setBooleanField " + signature + " = " + value);
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/os/Build$VERSION->SDK_INT:I":
                return 30;
            default:
                miss("getStaticIntField", signature, 0);
                return 0;
        }
    }

    // ================= helpers =================

    private DvmObject<?> context() {
        return vm.resolveClass("android/content/Context").newObject(null);
    }

    private DvmObject<?> fileObj(String path) {
        return vm.resolveClass("java/io/File").newObject(path);
    }

    public DvmObject<?> newContext() {
        return context();
    }

    public StringObject newString(String s) {
        return new StringObject(vm, s);
    }

    public DvmInteger newInt(int i) {
        return DvmInteger.valueOf(vm, i);
    }

    public DvmLong newLong(long l) {
        return DvmLong.valueOf(vm, l);
    }

    public DvmBoolean newBool(boolean b) {
        return DvmBoolean.valueOf(vm, b);
    }

    public ByteArray newBytes(byte[] b) {
        return new ByteArray(vm, b);
    }
}
