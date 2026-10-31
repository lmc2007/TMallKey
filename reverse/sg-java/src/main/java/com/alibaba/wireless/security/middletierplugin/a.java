package com.alibaba.wireless.security.middletierplugin;

import java.lang.reflect.Method;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class a {
    private static Object a;
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
                    a = method.invoke(cls, new Object[0]);
                }
            }
        } catch (Throwable unused) {
        }
    }

    public static void a(String str) {
        try {
            if (a == null || c == null) {
                return;
            }
            c.invoke(a, str);
        } catch (Exception unused) {
        }
    }

    public static void b(String str) {
        try {
            if (a == null || b == null) {
                return;
            }
            b.invoke(a, str);
        } catch (Exception unused) {
        }
    }
}
