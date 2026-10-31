package android.content.res;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * SG 会尝试从 assets 读 yw_1222.jpg。宿主 JVM 上没有 APK，直接给空流，
 * 真数据由 harness 的 IOResolver 走 /data/data/... 路径喂进去。
 */
public class AssetManager {

    private File assetDir = new File("tools/unidbg-tmall/assets");

    public void setAssetDir(File dir) {
        this.assetDir = dir;
    }

    public InputStream open(String fileName) throws IOException {
        File f = new File(assetDir, fileName);
        if (f.isFile()) {
            return new FileInputStream(f);
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    public InputStream open(String fileName, int accessMode) throws IOException {
        return open(fileName);
    }

    public String[] list(String path) throws IOException {
        String[] names = assetDir.list();
        return names == null ? new String[0] : names;
    }

    public void close() {
    }
}
