package com.tmall.sghook;

/**
 * 替换插件里的 System.loadLibrary。
 *
 * 真机上这里加载的是 libsgmainso / libsgsecuritybodyso；在「宿主 JVM 跑 Java 层」的方案里，
 * native 早已由 unidbg 加载并完成 JNI_OnLoad，宿主 JVM 再 loadLibrary 只会抛
 * UnsatisfiedLinkError，把整个 onPluginLoaded 打断。
 *
 * 所以把它抽成钩子：默认 no-op，需要时挂一个回调做记录。
 */
public final class NativeLibraryLoader {

    public interface Hook {
        void onLoad(String libraryName);
    }

    private static volatile Hook hook;

    private NativeLibraryLoader() {
    }

    public static void setHook(Hook h) {
        hook = h;
    }

    public static void load(String libraryName) {
        Hook h = hook;
        if (h != null) {
            h.onLoad(libraryName);
        }
    }
}
