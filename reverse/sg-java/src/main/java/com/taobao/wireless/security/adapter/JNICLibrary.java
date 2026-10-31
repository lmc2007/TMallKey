package com.taobao.wireless.security.adapter;

/**
 * 原类是 `public static native Object doCommandNative(int i, Object... objArr);`
 * （由 libsgmainso 通过 RegisterNatives 绑定）。
 *
 * 这里改成普通方法转发到 NativeBridge —— native 仍然由 unidbg 执行，只是入口从 JVM 进。
 */
public class JNICLibrary {

    public static Object doCommandNative(int i, Object... objArr) {
        return com.tmall.sghook.NativeBridge.dispatch(i, objArr);
    }
}
