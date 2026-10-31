package com.alibaba.wireless.security.securitybody.open;

import android.location.Location;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.securitybody.f;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class a {

    /* renamed from: a, reason: collision with root package name */
    private static Object f39a = null;
    private static a b = null;
    private static ISecurityGuardPlugin c = null;
    private static final String d = "initLBSManager";
    private static final String e = "putLocationData";
    private static final String f = "getLocationData";
    private static final String g = "clearLocationData";
    private static final String h = "toString";
    private static InvocationHandler i = new C0002a();

    /* renamed from: com.alibaba.wireless.security.securitybody.open.a$a, reason: collision with other inner class name */
    static class C0002a implements InvocationHandler {
        C0002a() {
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            char c;
            String name = method.getName();
            switch (name.hashCode()) {
                case -1776922004:
                    if (name.equals(a.h)) {
                        c = 4;
                        break;
                    }
                    c = 65535;
                    break;
                case -1602740244:
                    if (name.equals(a.g)) {
                        c = 3;
                        break;
                    }
                    c = 65535;
                    break;
                case -1543173714:
                    if (name.equals(a.e)) {
                        c = 1;
                        break;
                    }
                    c = 65535;
                    break;
                case -1036535712:
                    if (name.equals(a.d)) {
                        c = 0;
                        break;
                    }
                    c = 65535;
                    break;
                case 1567732597:
                    if (name.equals(a.f)) {
                        c = 2;
                        break;
                    }
                    c = 65535;
                    break;
                default:
                    c = 65535;
                    break;
            }
            if (c == 0) {
                return Boolean.valueOf(a.b.a((HashMap<String, Object>) objArr[0]));
            }
            if (c == 1) {
                return Boolean.valueOf(a.b.a((Location) objArr[0]));
            }
            if (c == 2) {
                return a.b.a(((Integer) objArr[0]).intValue());
            }
            if (c == 3) {
                return Boolean.valueOf(a.b.a());
            }
            if (c != 4) {
                return null;
            }
            return obj.getClass().getName() + "&ID=" + hashCode();
        }
    }

    private a(ISecurityGuardPlugin iSecurityGuardPlugin) {
        c = iSecurityGuardPlugin;
    }

    public static Object a(Class cls, ISecurityGuardPlugin iSecurityGuardPlugin) {
        if (f39a == null) {
            synchronized (a.class) {
                if (f39a == null) {
                    f39a = com.alibaba.wireless.security.securitybody.f.a(cls, i);
                    b = new a(iSecurityGuardPlugin);
                }
            }
        }
        return f39a;
    }

    public String a(int i2) {
        return "Abandoned";
    }

    public boolean a() {
        return true;
    }

    public boolean a(Location location) {
        return true;
    }

    public boolean a(HashMap<String, Object> hashMap) {
        return true;
    }
}
