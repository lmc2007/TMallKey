package com.alibaba.wireless.security.securitybody;

import java.lang.reflect.Method;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class a {

    /* renamed from: a, reason: collision with root package name */
    private static Object f31a;
    private static Method b;
    private static Method c;

    public static void a() {
        try {
            Class<?> cls = Class.forName("com.alibaba.wireless.security.framework.SGApmMonitorManager");
            if (cls != null) {
                Method method = cls.getMethod("getInstance", new Class[0]);
                b = cls.getMethod("monitorStart", String.class);
                c = cls.getMethod("monitorEnd", String.class);
                if (method != null) {
                    f31a = method.invoke(cls, new Object[0]);
                }
            }
        } catch (Throwable unused) {
        }
    }

    public static void a(String str) {
        try {
            if (f31a == null || c == null) {
                return;
            }
            c.invoke(f31a, str);
        } catch (Exception unused) {
        }
    }

    public static void b(String str) {
        try {
            if (f31a == null || b == null) {
                return;
            }
            b.invoke(f31a, str);
        } catch (Exception unused) {
        }
    }
}
