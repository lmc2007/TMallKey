package android.os;

import java.io.File;

public class StatFs {

    private final String path;
    private long blockCount = 62_000_000L;
    private long blockSize = 4096L;
    private long availableBlocks = 12_000_000L;

    public StatFs(String path) {
        this.path = path;
    }

    public StatFs(File file) {
        this.path = file == null ? "/data" : file.getPath();
    }

    public void restat(String path) {
    }

    public String getPath() {
        return path;
    }

    public int getBlockSize() {
        return (int) blockSize;
    }

    public int getBlockCount() {
        return (int) blockCount;
    }

    public int getAvailableBlocks() {
        return (int) availableBlocks;
    }

    public long getBlockCountLong() {
        return blockCount;
    }

    public long getBlockSizeLong() {
        return blockSize;
    }

    public long getAvailableBlocksLong() {
        return availableBlocks;
    }

    public long getAvailableBytes() {
        return availableBlocks * blockSize;
    }

    public long getTotalBytes() {
        return blockCount * blockSize;
    }

    public long getFreeBytes() {
        return getAvailableBytes();
    }
}
