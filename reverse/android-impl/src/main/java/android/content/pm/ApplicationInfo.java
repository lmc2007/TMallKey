package android.content.pm;

public class ApplicationInfo {

    public String packageName;
    public String sourceDir;
    public String publicSourceDir;
    public String dataDir;
    public String nativeLibraryDir;
    public String processName;
    public String className;
    public int flags;
    public int targetSdkVersion;
    public int minSdkVersion;
    public String[] splitSourceDirs;

    public static final int FLAG_DEBUGGABLE = 2;
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_EXTRACT_NATIVE_LIBS = 0x10000000;

    public static ApplicationInfo of(String packageName) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.processName = packageName;
        info.className = "";
        info.sourceDir = "/data/app/" + packageName + "-1/base.apk";
        info.publicSourceDir = info.sourceDir;
        info.dataDir = "/data/data/" + packageName;
        info.nativeLibraryDir = "/data/app/" + packageName + "-1/lib/arm64";
        info.targetSdkVersion = 30;
        info.minSdkVersion = 21;
        return info;
    }
}
