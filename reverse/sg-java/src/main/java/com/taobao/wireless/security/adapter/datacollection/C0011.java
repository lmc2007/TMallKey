package com.taobao.wireless.security.adapter.datacollection;

import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.adapter.common.SPUtility2;
import com.taobao.wireless.security.adapter.datareport.DataReportJniBridge;

/* renamed from: com.taobao.wireless.security.adapter.datacollection.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0011 {

    /* renamed from: б, reason: contains not printable characters */
    private static final Object f40 = new Object();

    /* renamed from: в, reason: contains not printable characters */
    private static C0011 f41;

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f42;

    private C0011(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f42 = iSecurityGuardPlugin;
        DeviceInfoCapturer.initialize(this.f42, this);
        DataReportJniBridge.initialize(iSecurityGuardPlugin.getContext());
    }

    /* renamed from: а, reason: contains not printable characters */
    public static C0011 m44(ISecurityGuardPlugin iSecurityGuardPlugin) {
        if (f41 == null) {
            synchronized (C0011.class) {
                if (f41 == null) {
                    f41 = new C0011(iSecurityGuardPlugin);
                }
            }
        }
        return f41;
    }

    /* renamed from: б, reason: contains not printable characters */
    private String m45() {
        return "unknown";
    }

    /* renamed from: б, reason: contains not printable characters */
    private void m46(int i) {
        this.f42.getRouter().doCommand(10901, new Object[]{Integer.valueOf(i)});
    }

    /* renamed from: в, reason: contains not printable characters */
    private String m47() {
        try {
            return (String) Class.forName("com.taobao.login4android.Login").getMethod("getNick", new Class[0]).invoke(null, new Object[0]);
        } catch (Exception unused) {
            return null;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    public String m48() {
        return m49(0);
    }

    /* renamed from: а, reason: contains not printable characters */
    public String m49(int i) {
        String str;
        synchronized (f40) {
            str = null;
            try {
                if ((i & 64) != 0) {
                    str = m45();
                } else if (i == 0) {
                    str = m47();
                }
                if (str == null || str.length() == 0) {
                    String str2 = "key_nick";
                    if (i != 0) {
                        str2 = "key_nick_" + i;
                    }
                    str = SPUtility2.readFromSPUnified("DataCollectionData", str2, "");
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return str;
    }

    /* renamed from: а, reason: contains not printable characters */
    public boolean m50(int i, String str) {
        boolean z;
        String m49 = m49(i);
        synchronized (f40) {
            if (str == null) {
                str = "";
            }
            String str2 = "key_nick";
            if (i != 0) {
                str2 = "key_nick_" + i;
            }
            z = !str.equals(m49) && SPUtility2.saveToSPUnified("DataCollectionData", str2, str, true);
        }
        if (!z) {
            return false;
        }
        m46(i);
        return true;
    }

    /* renamed from: а, reason: contains not printable characters */
    public boolean m51(String str) {
        return m50(0, str);
    }
}
