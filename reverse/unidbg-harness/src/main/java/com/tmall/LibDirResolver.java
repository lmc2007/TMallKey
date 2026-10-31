package com.tmall;

import com.github.unidbg.Emulator;
import com.github.unidbg.LibraryResolver;
import com.github.unidbg.spi.LibraryFile;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * 让 native 自己的 dlopen 能找到提取出来的插件 so。
 *
 * 关键点：SecurityGuard 的 2xxxx（securitybody）命令处理器，是在 body so 被 **native dlopen**
 * 的那一刻注册进主模块分发表的。我们之前用 vm.loadLibrary 手工装进去，等于跳过了注册链，
 * 主模块查不到 2xxxx → 返回 9904。这里把库搜索路径指到提取目录，让 native 自己加载。
 */
public class LibDirResolver implements LibraryResolver {

    private final File libDir;
    private final LibraryResolver fallback;

    public LibDirResolver(File libDir, LibraryResolver fallback) {
        this.libDir = libDir;
        this.fallback = fallback;
    }

    @Override
    public LibraryFile resolveLibrary(Emulator<?> emulator, String libraryName) {
        File f = new File(libDir, libraryName);
        if (f.isFile()) {
            return new SimpleLibraryFile(f, libraryName);
        }
        return fallback == null ? null : fallback.resolveLibrary(emulator, libraryName);
    }

    @Override
    public void onSetToLoader(Emulator<?> emulator) {
        if (fallback != null) {
            fallback.onSetToLoader(emulator);
        }
    }

    /** 最小 LibraryFile 实现：直接从磁盘映射。 */
    static final class SimpleLibraryFile implements LibraryFile {

        private final File file;
        private final String name;

        SimpleLibraryFile(File file, String name) {
            this.file = file;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getMapRegionName() {
            return file.getAbsolutePath();
        }

        @Override
        public LibraryFile resolveLibrary(Emulator<?> emulator, String libraryName) {
            File f = new File(file.getParentFile(), libraryName);
            return f.isFile() ? new SimpleLibraryFile(f, libraryName) : null;
        }

        @Override
        public ByteBuffer mapBuffer() throws IOException {
            try (FileChannel ch = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                return ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
            }
        }

        @Override
        public String getPath() {
            return file.getAbsolutePath();
        }

        @Override
        public long getFileSize() {
            return file.length();
        }
    }
}
