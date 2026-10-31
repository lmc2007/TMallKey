package com.alibaba.wireless.security.middletierplugin;

import android.content.Context;
import com.alibaba.wireless.security.framework.IRouterComponent;
import com.alibaba.wireless.security.framework.ISGPluginInfo;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.concurrent.Executors;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class SecurityGuardMiddleTierPlugin implements ISecurityGuardPlugin {
    private HashMap<Class, Object> a = null;
    private Context b = null;
    private ISGPluginInfo c = null;
    private IRouterComponent d = null;

    class a implements Runnable {
        a() {
        }

        @Override // java.lang.Runnable
        public void run() {
            try {
                // c.a.* 是 FC/ES 风控的延迟初始化（WebView cookie 嗅探），与 getSecurityFactors 无关，已裁剪
            } catch (Throwable unused) {
            }
        }
    }

    public static Object getProxyInstance(Class cls, InvocationHandler invocationHandler) {
        return Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, invocationHandler);
    }

    public Context getContext() {
        return this.b;
    }

    public <T> T getInterface(Class<T> cls) {
        T t;
        if (cls == null || (t = (T) this.a.get(cls)) == null || !cls.isAssignableFrom(t.getClass())) {
            return null;
        }
        return t;
    }

    public IRouterComponent getRouter() {
        return this.d;
    }

    public ISGPluginInfo getSGPluginInfo() {
        return this.c;
    }

    public IRouterComponent onPluginLoaded(Context context, IRouterComponent iRouterComponent, ISGPluginInfo iSGPluginInfo, String str, Object... objArr) {
        this.d = iRouterComponent;
        this.b = context;
        this.c = iSGPluginInfo;
        this.a = new HashMap<>();
        com.alibaba.wireless.security.middletierplugin.a.a();
        new b().a(this.a, this, context);
        com.tmall.sghook.NativeLibraryLoader.load(str);
        Executors.newFixedThreadPool(1).execute(new a());
        return iRouterComponent;
    }
}
