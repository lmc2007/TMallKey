package com.alibaba.wireless.security.middletierplugin;

import android.content.Context;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class b {
    public static Object a(Class cls, InvocationHandler invocationHandler) {
        return Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, invocationHandler);
    }

    public void a(HashMap<Class, Object> hashMap, SecurityGuardMiddleTierPlugin securityGuardMiddleTierPlugin, Context context) {
        Object proxyInstance;
        try {
            Class<?> cls3 = Class.forName("com.alibaba.wireless.security.open.middletier.IUnifiedSecurityComponent");
            if (cls3 != null) {
                hashMap.put(cls3, com.alibaba.wireless.security.middletierplugin.d.d.a.a(cls3, securityGuardMiddleTierPlugin, context));
            }
        } catch (Exception unused2) {
        }
    }
}
