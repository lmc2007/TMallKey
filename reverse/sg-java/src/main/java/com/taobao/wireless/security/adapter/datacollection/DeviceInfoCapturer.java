package com.taobao.wireless.security.adapter.datacollection;

import android.content.Context;
import com.alibaba.wireless.security.mainplugin.SecurityGuardMainPlugin;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class DeviceInfoCapturer {

    /* renamed from: а, reason: contains not printable characters */
    private static Context f35 = null;

    /* renamed from: б, reason: contains not printable characters */
    private static C0011 f36 = null;

    /* renamed from: в, reason: contains not printable characters */
    private static SecurityGuardMainPlugin f37 = null;

    /* renamed from: г, reason: contains not printable characters */
    private static volatile boolean f38 = false;

    /* renamed from: д, reason: contains not printable characters */
    private static Object f39;

    private DeviceInfoCapturer() {
    }

    public static String doCommandForString(int i) {
        if (!f38) {
            synchronized (f39) {
                if (!f38) {
                    C0013.m57(f35);
                    C0014.m61(f35);
                    C0016.m68(f35);
                    C0012.m54(f35);
                    C0015.m65(f35);
                    f38 = true;
                }
            }
        }
        String str = null;
        try {
            if (i == 0) {
                str = C0013.m58("android.hardware.wifi") ? "1" : "0";
            } else if (i == 109) {
                str = C0016.m69();
            } else if (i == 126) {
                str = m43();
            } else if (i == 130) {
                str = C0012.m52();
            } else if (i == 135) {
                str = C0015.m64();
            } else if (i == 146) {
                str = C0012.m55();
            } else if (i == 104) {
                str = C0014.m60();
            } else if (i != 105) {
                switch (i) {
                    case 121:
                        str = m42();
                        break;
                    case 122:
                        str = C0013.m59();
                        break;
                    case 123:
                        str = C0013.m56();
                        break;
                }
            } else {
                str = C0014.m62();
            }
        } catch (Exception unused) {
        }
        return str;
    }

    public static void initialize(ISecurityGuardPlugin iSecurityGuardPlugin, C0011 c0011) {
        f37 = (SecurityGuardMainPlugin) iSecurityGuardPlugin;
        f35 = f37.getContext();
        f36 = c0011;
        f39 = new Object();
    }

    /* renamed from: а, reason: contains not printable characters */
    private static String m42() {
        C0011 c0011 = f36;
        if (c0011 != null) {
            return c0011.m48();
        }
        return null;
    }

    /* renamed from: б, reason: contains not printable characters */
    private static String m43() {
        C0011 c0011 = f36;
        if (c0011 != null) {
            return c0011.m49(64);
        }
        return null;
    }
}
