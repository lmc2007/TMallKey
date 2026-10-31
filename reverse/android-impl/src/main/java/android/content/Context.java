package android.content;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 宿主 JVM 上的 Context 实现。
 *
 * 只覆盖 SecurityGuard 的 Java 层真正会用到的面：包名、私有目录、SharedPreferences、
 * PackageManager、权限检查、SystemService。数据全部来自进程内内存，不碰真实文件系统
 * （除了 getFilesDir 指向的本地目录，由 harness 的 IOResolver 映射）。
 */
public class Context {

    public static final String TELEPHONY_SERVICE = "phone";
    public static final String WIFI_SERVICE = "wifi";
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String LOCATION_SERVICE = "location";
    public static final String WINDOW_SERVICE = "window";
    public static final String SENSOR_SERVICE = "sensor";
    public static final String KEYGUARD_SERVICE = "keyguard";
    public static final String AUDIO_SERVICE = "audio";

    public static final int MODE_PRIVATE = 0;
    public static final int MODE_APPEND = 32768;
    public static final int MODE_MULTI_PROCESS = 4;

    private static final Map<String, SharedPreferences> PREFS = new HashMap<>();

    private String packageName = "com.tmall.campus";
    private File filesDir;
    private File cacheDir;
    private String dataDir = "/data/data/com.tmall.campus";

    public Context() {
        this.filesDir = new File(dataDir, "files");
        this.cacheDir = new File(dataDir, "cache");
    }

    public Context(String packageName, String dataDir) {
        this.packageName = packageName;
        this.dataDir = dataDir;
        this.filesDir = new File(dataDir, "files");
        this.cacheDir = new File(dataDir, "cache");
    }

    public String getPackageName() {
        return packageName;
    }

    public String getOpPackageName() {
        return packageName;
    }

    public Context getApplicationContext() {
        return this;
    }

    public File getFilesDir() {
        return filesDir;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public File getCodeCacheDir() {
        return new File(dataDir, "code_cache");
    }

    public File getDir(String name, int mode) {
        return new File(dataDir, "app_" + name);
    }

    public File getDatabasePath(String name) {
        return new File(dataDir + "/databases/" + name);
    }

    public String getDataDir() {
        return dataDir;
    }

    public String getPackageCodePath() {
        return "/data/app/" + packageName + "-1/base.apk";
    }

    public String getPackageResourcePath() {
        return getPackageCodePath();
    }

    public synchronized SharedPreferences getSharedPreferences(String name, int mode) {
        return PREFS.computeIfAbsent(name, SharedPreferences::new);
    }

    public android.content.pm.PackageManager getPackageManager() {
        return new android.content.pm.PackageManager();
    }

    public android.content.pm.ApplicationInfo getApplicationInfo() {
        return android.content.pm.ApplicationInfo.of(packageName);
    }

    public android.content.res.AssetManager getAssets() {
        return new android.content.res.AssetManager();
    }

    public ClassLoader getClassLoader() {
        return Context.class.getClassLoader();
    }

    public int checkSelfPermission(String permission) {
        return 0; // PERMISSION_GRANTED
    }

    public int checkCallingOrSelfPermission(String permission) {
        return 0;
    }

    public Object getSystemService(String name) {
        if (TELEPHONY_SERVICE.equals(name)) {
            return new android.telephony.TelephonyManager();
        }
        if (WIFI_SERVICE.equals(name)) {
            return new android.net.wifi.WifiManager();
        }
        if (ACTIVITY_SERVICE.equals(name)) {
            return new android.app.ActivityManager();
        }
        if (WINDOW_SERVICE.equals(name)) {
            return new android.view.WindowManager();
        }
        return null;
    }

    /** SecurityBodyAdapter 用它读电量，这里固定返回“有电、未充电”。 */
    public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver,
                                                   android.content.IntentFilter filter) {
        return new android.content.Intent();
    }

    public void startActivity(android.content.Intent intent) {
    }

    public Object getContentResolver() {
        return new Object();
    }

    public String getString(int resId) {
        return "";
    }
}
