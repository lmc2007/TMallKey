package android;

import java.io.File;

/**
 * 一个"长得像 File、但路径永远是 Android 风格"的 File。
 *
 * 宿主 JVM 在 Windows 上，`new File("/data/data/x", "files").getAbsolutePath()`
 * 会变成 `C:\data\data\x\files`。SG 的 native 用法是 `context.getFilesDir().getAbsolutePath()`
 * 再拼子路径，一旦混进盘符，open 就全部落到错误位置（还会被 IOResolver 当成相对路径）。
 *
 * 所以这里把路径字符串原样保留，只借 File 的类型给 Java 侧用。
 */
public class AndroidPathFile extends File {

    private static final long serialVersionUID = 1L;

    private final String androidPath;

    public AndroidPathFile(String androidPath) {
        super(androidPath);
        this.androidPath = androidPath;
    }

    @Override
    public String getPath() {
        return androidPath;
    }

    @Override
    public String getAbsolutePath() {
        return androidPath;
    }

    @Override
    public String getCanonicalPath() {
        return androidPath;
    }

    @Override
    public String toString() {
        return androidPath;
    }
}
