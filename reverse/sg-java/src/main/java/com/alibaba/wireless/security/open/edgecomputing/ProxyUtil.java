package com.alibaba.wireless.security.open.edgecomputing;

import android.os.Handler;
import android.util.Base64;
import com.alibaba.wireless.security.open.SecException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class ProxyUtil {

    /* renamed from: а, reason: contains not printable characters */
    private static volatile Handler f16;

    /* renamed from: com.alibaba.wireless.security.open.edgecomputing.ProxyUtil$а, reason: contains not printable characters */
    static class C0005 implements InvocationHandler {

        /* renamed from: а, reason: contains not printable characters */
        final /* synthetic */ Object f17;

        /* renamed from: б, reason: contains not printable characters */
        final /* synthetic */ int f18;

        C0005(Object obj, int i) {
            this.f17 = obj;
            this.f18 = i;
        }

        /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) throws Throwable {
            String name = method.getName();
            Object obj2 = this.f17;
            try {
                ProxyUtil.m18(this.f18, obj, name, objArr, obj2 != null ? obj2.getClass().getCanonicalName() : null);
            } catch (SecException unused) {
            }
            Object obj3 = this.f17;
            if (obj3 != null) {
                return method.invoke(obj3, objArr);
            }
            String name2 = method.getReturnType().getName();
            char c = 65535;
            switch (name2.hashCode()) {
                case -1808118735:
                    if (name2.equals("String")) {
                        c = 2;
                        break;
                    }
                    break;
                case -672261858:
                    if (name2.equals("Integer")) {
                        c = 3;
                        break;
                    }
                    break;
                case 104431:
                    if (name2.equals("int")) {
                        c = 1;
                        break;
                    }
                    break;
                case 64711720:
                    if (name2.equals("boolean")) {
                        c = 0;
                        break;
                    }
                    break;
                case 1729365000:
                    if (name2.equals("Boolean")) {
                        c = 4;
                        break;
                    }
                    break;
            }
            if (c == 0) {
                return false;
            }
            if (c != 1) {
                if (c == 2) {
                    return "";
                }
                if (c != 3) {
                    if (c != 4) {
                        return null;
                    }
                    return Boolean.FALSE;
                }
            }
            return 0;
        }
    }

    public static Handler getHandler() {
        return f16;
    }

    public static Object getProxyInstance(String str, Object obj, int i) {
        try {
            Class<?> cls = Class.forName(new String(Base64.decode(str, 0)));
            if (cls == null) {
                return null;
            }
            return Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, new C0005(obj, i));
        } catch (Exception unused) {
            return null;
        }
    }

    public static void init(Handler handler) {
        f16 = handler;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: б, reason: contains not printable characters */
    public static void m18(int i, Object obj, String str, Object[] objArr, String str2) throws SecException {
        C0006.m21().doCommand(i, new Object[]{obj, str, objArr, str2});
    }
}
