package android.content.pm;

public class PackageInfo {

    public String packageName;
    public String versionName;
    public long versionCode;
    public int minSdkVersion;
    public int targetSdkVersion;
    public ApplicationInfo applicationInfo;
    public Signature[] signatures;
    public long firstInstallTime;
    public long lastUpdateTime;

    public static PackageInfo of(String packageName) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.versionName = "5.0.0";
        info.versionCode = 5000000L;
        info.minSdkVersion = 21;
        info.targetSdkVersion = 30;
        info.applicationInfo = ApplicationInfo.of(packageName);
        info.signatures = new Signature[]{new Signature(Signature.official())};
        info.firstInstallTime = 1600000000000L;
        info.lastUpdateTime = 1600000000000L;
        return info;
    }

    public long getLongVersionCode() {
        return versionCode;
    }
}
