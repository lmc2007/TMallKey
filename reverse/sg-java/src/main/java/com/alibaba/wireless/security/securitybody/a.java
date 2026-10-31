package com.alibaba.wireless.security.securitybody;

import java.lang.reflect.Method;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class a {

    /* renamed from: a, reason: collision with root package name */
    private static Object f31a;
    private static Method MONITOR_START;
    private static Method MONITOR_END;

    public static void a() {
        try {
            Class<?> cls = Class.forName("com.alibaba.wireless.security.framework.SGApmMonitorManager");
            if (cls != null) {
                Method method = cls.getMethod("getInstance", new Class[0]);
                MONITOR_START = cls.getMethod("monitorStart", String.class);
                MONITOR_END = cls.getMethod("monitorEnd", String.class);
                if (method != null) {
                    f31a = method.invoke(cls, new Object[0]);
                }
            }
        } catch (Throwable unused) {
        }
    }

    public static void a(String str) {
        try {
            if (f31a == null || MONITOR_END == null) {
                return;
            }
            MONITOR_END.invoke(f31a, str);
        } catch (Exception unused) {
        }
    }

    public static void b(String str) {
        try {
            if (f31a == null || MONITOR_START == null) {
                return;
            }
            MONITOR_START.invoke(f31a, str);
        } catch (Exception unused) {
        }
    }
}
