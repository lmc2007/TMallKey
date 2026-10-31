package com.alibaba.wireless.security.middletierplugin.d.c;

import android.content.Context;
import com.alibaba.wireless.security.middletierplugin.b;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class a {
    private static ISecurityGuardPlugin a;
    private static a b;
    private static Object c;
    private static InvocationHandler d = new C0004a();

    /* renamed from: com.alibaba.wireless.security.middletierplugin.d.c.a$a, reason: collision with other inner class name */
    static class C0004a implements InvocationHandler {
        C0004a() {
        }

        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            char c;
            String name = method.getName();
            int hashCode = name.hashCode();
            if (hashCode != -309518737) {
                if (hashCode == 3237136 && name.equals("init")) {
                    c = 0;
                }
                c = 65535;
            } else {
                if (name.equals("process")) {
                    c = 1;
                }
                c = 65535;
            }
            if (c == 0) {
                a.b.a((HashMap<String, Object>) objArr[0]);
                return null;
            }
            if (c != 1) {
                return null;
            }
            return a.b.a(Integer.parseInt(String.valueOf(objArr[0])));
        }
    }

    private a(ISecurityGuardPlugin iSecurityGuardPlugin) {
        a = iSecurityGuardPlugin;
    }

    public static Object a(Class cls, ISecurityGuardPlugin iSecurityGuardPlugin, Context context) {
        if (c == null) {
            synchronized (a.class) {
                if (c == null) {
                    c = b.a(cls, d);
                    b = new a(iSecurityGuardPlugin);
                }
            }
        }
        return c;
    }

    public HashMap<String, Object> a(int i) {
        HashMap<String, Object> hashMap = new HashMap<>();
        if (i != 1 && i != 2) {
            if (i == 3) {
                byte[] bArr = (byte[]) a.getRouter().doCommand(70702, new Object[]{Integer.valueOf(i)});
                if (bArr != null) {
                    hashMap.put("data", new String(bArr));
                }
                return hashMap;
            }
            if (i != 4) {
                throw new SecException(2801);
            }
        }
        hashMap.put("processResult", Boolean.valueOf(((Boolean) a.getRouter().doCommand(70702, new Object[]{Integer.valueOf(i)})).booleanValue()));
        return hashMap;
    }

    public void a(HashMap<String, Object> hashMap) {
        if (hashMap == null) {
            throw new SecException(2801);
        }
        a.getRouter().doCommand(70701, new Object[]{(String) hashMap.get("config"), (String) hashMap.get("token")});
    }
}
