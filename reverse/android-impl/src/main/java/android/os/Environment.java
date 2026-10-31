package android.os;

import java.io.File;

public class Environment {

    public static final String MEDIA_MOUNTED = "mounted";

    public static File getExternalStorageDirectory() {
        return new File("/sdcard");
    }

    public static File getDataDirectory() {
        return new File("/data");
    }

    public static File getDownloadCacheDirectory() {
        return new File("/cache");
    }

    public static String getExternalStorageState() {
        return MEDIA_MOUNTED;
    }

    public static boolean isExternalStorageEmulated() {
        return true;
    }
}
