package com.alibaba.wireless.security.mainplugin;

import java.lang.reflect.Method;

/* renamed from: com.alibaba.wireless.security.mainplugin.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0000 {

    /* renamed from: а, reason: contains not printable characters */
    private static Object f6;

    /* renamed from: б, reason: contains not printable characters */
    private static Method f7;

    /* renamed from: в, reason: contains not printable characters */
    private static Method f8;

    /* renamed from: а, reason: contains not printable characters */
    public static void m2() {
        try {
            Class<?> cls = Class.forName("com.alibaba.wireless.security.framework.SGApmMonitorManager");
            if (cls != null) {
                Method method = cls.getMethod("getInstance", new Class[0]);
                f7 = cls.getMethod("monitorStart", String.class);
                f8 = cls.getMethod("monitorEnd", String.class);
                if (method != null) {
                    f6 = method.invoke(cls, new Object[0]);
                }
            }
        } catch (Throwable unused) {
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    public static void m3(String str) {
        try {
            if (f6 == null || f8 == null) {
                return;
            }
            f8.invoke(f6, str);
        } catch (Exception unused) {
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    public static void m4(String str) {
        try {
            if (f6 == null || f7 == null) {
                return;
            }
            f7.invoke(f6, str);
        } catch (Exception unused) {
        }
    }
}
