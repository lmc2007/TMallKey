package com.alibaba.wireless.security.mainplugin;

import android.content.Context;
import com.alibaba.wireless.security.framework.IRouterComponent;
import com.alibaba.wireless.security.framework.ISGPluginInfo;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.edgecomputing.C0006;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.adapter.common.C0009;
import com.taobao.wireless.security.adapter.common.SPUtility2;
import com.taobao.wireless.security.adapter.datareport.C0017;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class SecurityGuardMainPlugin implements ISecurityGuardPlugin {
    public static ClassLoader sClassLoader;
    public static ClassLoader sParentClassLoader;

    /* renamed from: а, reason: contains not printable characters */
    private HashMap<Class, Object> f0 = null;

    /* renamed from: б, reason: contains not printable characters */
    private boolean f1 = true;

    /* renamed from: в, reason: contains not printable characters */
    private Context f2 = null;

    /* renamed from: г, reason: contains not printable characters */
    private ISGPluginInfo f3 = null;

    /* renamed from: д, reason: contains not printable characters */
    private IRouterComponent f4 = null;

    /* renamed from: е, reason: contains not printable characters */
    private C0002 f5 = new C0002();

    public static ClassLoader getMainPluginClassLoader() {
        return sClassLoader;
    }

    public static ClassLoader getMainPluginParentClassLoader() {
        return sParentClassLoader;
    }

    /* renamed from: а, reason: contains not printable characters */
    private void m0() {
        this.f0 = new HashMap<>();
        this.f5.m6(this.f0, this);
    }

    /* renamed from: а, reason: contains not printable characters */
    private void m1(String str) {
        if (str == null) {
            return;
        }
        this.f5.m7(this.f0, str, this);
    }

    public Context getContext() {
        return this.f2;
    }

    public <T> T getInterface(Class<T> cls) {
        if (cls == null) {
            return null;
        }
        T t = (T) this.f0.get(cls);
        if (t != null && cls.isAssignableFrom(t.getClass())) {
            return t;
        }
        try {
            m1(cls.getName());
            return (T) this.f0.get(cls);
        } catch (Exception unused) {
            return null;
        }
    }

    public IRouterComponent getRouter() {
        return this.f4;
    }

    public ISGPluginInfo getSGPluginInfo() {
        return this.f3;
    }

    public IRouterComponent onPluginLoaded(Context context, IRouterComponent iRouterComponent, ISGPluginInfo iSGPluginInfo, String str, Object... objArr) throws SecException {
        String str2;
        String str3;
        int i;
        if (this.f1) {
            this.f2 = context;
            this.f3 = iSGPluginInfo;
            C0000.m2();
            try {
                sClassLoader = getClass().getClassLoader();
                if (sClassLoader != null) {
                    sParentClassLoader = sClassLoader.getParent();
                }
            } catch (Throwable unused) {
            }
            SPUtility2.init(context);
            C0009.m36(context);
            m0();
            this.f4 = new C0001();
            C0006.m20(context, this.f4, this);
            try {
                System.loadLibrary(str);
                String str4 = "";
                if (objArr.length >= 4 && (objArr[0] instanceof Integer) && (objArr[1] instanceof String) && (objArr[2] instanceof String)) {
                    i = ((Integer) objArr[0]).intValue();
                    str2 = (String) objArr[1];
                    str3 = (String) objArr[2];
                    if (objArr[3] instanceof String) {
                        str4 = (String) objArr[3];
                    }
                } else {
                    str2 = "";
                    str3 = str2;
                    i = 0;
                }
                int intValue = ((Integer) getRouter().doCommand(10101, new Object[]{context, Integer.valueOf(i), str2, str3, str4})).intValue();
                if (intValue != 0) {
                    throw new SecException(intValue);
                }
                C0017.m75(this);
                this.f1 = false;
            } catch (Throwable th) {
                throw new SecException(th.toString(), 103);
            }
        }
        return this.f4;
    }
}
