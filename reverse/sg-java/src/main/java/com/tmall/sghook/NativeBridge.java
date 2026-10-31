package com.tmall.sghook;

/**
 * 宿主 JVM 与 unidbg 之间的唯一接口。
 *
 * 真机上 JNICLibrary.doCommandNative 是 native 方法，由 libsgmainso 实现；这里改成
 * 普通方法转发到本桥，由 harness 把参数包成 DVM 对象后调用 unidbg 里的同名 native。
 *
 * 数据流：
 *   SG 的 Java 代码 → NativeBridge → unidbg(doCommandNative) → 官方 SO
 *        ↑                                                    │
 *        └──────────── JNI 回调（ProxyJni 反射回宿主 JVM）──────┘
 */
public final class NativeBridge {

    public interface Handler {
        Object dispatch(int code, Object[] args);
    }

    private static volatile Handler handler;

    private NativeBridge() {
    }

    public static void setHandler(Handler h) {
        handler = h;
    }

    public static Object dispatch(int code, Object[] args) {
        Handler h = handler;
        if (h == null) {
            throw new IllegalStateException("NativeBridge handler 未设置");
        }
        return h.dispatch(code, args);
    }
}
