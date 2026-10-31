package android.content.pm;

import java.util.ArrayList;
import java.util.List;

public class PackageManager {

    public static final int GET_SIGNATURES = 0x00000040;
    public static final int GET_ACTIVITIES = 0x00000001;
    public static final int GET_META_DATA = 0x00000080;
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000;
    public static final int MATCH_SYSTEM_ONLY = 0x00100000;

    public static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    public static final String FEATURE_WIFI = "android.hardware.wifi";

    public PackageInfo getPackageInfo(String packageName, int flags)
            throws NameNotFoundException {
        if (packageName == null) {
            throw new NameNotFoundException();
        }
        return PackageInfo.of(packageName);
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws NameNotFoundException {
        if (packageName == null) {
            throw new NameNotFoundException();
        }
        return ApplicationInfo.of(packageName);
    }

    public List<PackageInfo> getInstalledPackages(int flags) {
        List<PackageInfo> list = new ArrayList<>();
        list.add(PackageInfo.of("com.tmall.campus"));
        return list;
    }

    public List<android.content.pm.ApplicationInfo> getInstalledApplications(int flags) {
        List<android.content.pm.ApplicationInfo> list = new ArrayList<>();
        list.add(ApplicationInfo.of("com.tmall.campus"));
        return list;
    }

    public boolean hasSystemFeature(String feature) {
        return FEATURE_TELEPHONY.equals(feature) || FEATURE_WIFI.equals(feature);
    }

    public String[] getSystemSharedLibraryNames() {
        return new String[0];
    }

    public String getInstallerPackageName(String packageName) {
        return null;
    }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() {
            super();
        }

        public NameNotFoundException(String name) {
            super(name);
        }
    }
}
