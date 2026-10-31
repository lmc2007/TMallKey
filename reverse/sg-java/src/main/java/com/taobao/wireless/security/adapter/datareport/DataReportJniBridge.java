package com.taobao.wireless.security.adapter.datareport;

import android.content.Context;
import android.util.Log;
import com.taobao.wireless.security.adapter.common.C0007;
import com.taobao.wireless.security.adapter.common.C0008;
import com.taobao.wireless.security.adapter.common.C0009;
import com.taobao.wireless.security.adapter.datareport.C0017;
import java.lang.reflect.Method;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class DataReportJniBridge {

    /* renamed from: а, reason: contains not printable characters */
    private static volatile int f55;

    /* renamed from: б, reason: contains not printable characters */
    private static volatile int f56;

    /* renamed from: в, reason: contains not printable characters */
    private static Class f57;

    /* renamed from: г, reason: contains not printable characters */
    private static Method f58;

    /* renamed from: д, reason: contains not printable characters */
    private static volatile int f59;

    /* renamed from: е, reason: contains not printable characters */
    private static volatile int f60;

    /* renamed from: ж, reason: contains not printable characters */
    private static volatile int f61;

    /* renamed from: з, reason: contains not printable characters */
    private static Class f62;

    /* renamed from: и, reason: contains not printable characters */
    private static Class f63;

    /* renamed from: й, reason: contains not printable characters */
    private static Method f64;

    /* renamed from: к, reason: contains not printable characters */
    private static Method f65;

    /* renamed from: л, reason: contains not printable characters */
    private static Context f66;

    /* renamed from: ё, reason: contains not printable characters */
    private static volatile int f67;

    public static int accsAvaiableBridge() {
        if (f56 == 0) {
            synchronized (DataReportJniBridge.class) {
                if (f56 == 0) {
                    try {
                        f57 = Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.AccsAdapter");
                        Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.AccsListner");
                        f58 = f57.getMethod("registerListner", Context.class);
                        f55 = 1;
                    } catch (ClassNotFoundException | NoSuchMethodException unused) {
                    }
                    f56 = 1;
                }
            }
        }
        return f55;
    }

    public static void initialize(Context context) {
        f66 = context;
    }

    public static int orangeAvailableBridge() {
        if (f67 == 0) {
            synchronized (DataReportJniBridge.class) {
                if (f67 == 0) {
                    try {
                        f62 = Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.OrangeAdapter");
                        Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.OrangeListener");
                        f64 = f62.getMethod("registerListener", Context.class);
                        f59 = 1;
                    } catch (ClassNotFoundException | NoSuchMethodException unused) {
                    }
                    f67 = 1;
                }
            }
        }
        return f59;
    }

    public static int registerAccsListnerBridge() {
        try {
            if (accsAvaiableBridge() != 0 && f66 != null) {
                f58.invoke(f57, f66);
                return 1;
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }
    }

    public static int registerOrangeListenerBridge() {
        try {
            if (orangeAvailableBridge() != 0 && f66 != null) {
                f64.invoke(f62, f66);
                return 1;
            }
            return 0;
        } catch (Exception unused) {
            return 0;
        }
    }

    public static int registerWindVaneListenerBridge() {
        int i = 0;
        try {
            if (windVaneCallBackAvailableBridge() != 0 && f66 != null) {
                f65.invoke(f63, f66);
                try {
                    Log.e("DataReportJniBridge", "registerWindVaneListenerBridge invoke success !!!");
                    return 1;
                } catch (Exception e) {
                    e = e;
                    i = 1;
                    Log.e("DataReportJniBridge", e.toString());
                    return i;
                }
            }
            return 0;
        } catch (Exception e2) {
            e = e2;
        }
    }

    public static String sendReportBridge(String str, String str2, byte[] bArr) {
        HashMap hashMap;
        String str3;
        String str4;
        C0017.C0020 c0020 = new C0017.C0020();
        if (C0007.m30(str2)) {
            hashMap = new HashMap();
            hashMap.put("keyindex", str2);
        } else {
            hashMap = null;
        }
        long currentTimeMillis = System.currentTimeMillis();
        C0008 m33 = C0009.m33(str, hashMap, bArr, null, 11000, 11000);
        long currentTimeMillis2 = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(m33.f25));
        sb.append("|");
        if (C0017.m79()) {
            c0020.m88(str);
            c0020.m81(bArr != null ? bArr.length : 0);
            c0020.m83(C0017.EnumC0021.https);
            c0020.m86(m33.f25);
            String str5 = m33.f24;
            c0020.m90(str5 != null ? str5.length() : 0);
            c0020.m82(currentTimeMillis2 - currentTimeMillis);
            c0020.m87(currentTimeMillis);
            C0017.m76(c0020);
        }
        int i = m33.f25;
        if (i != 200) {
            if (i == 1002 && (str3 = m33.f26) != null && str3.length() > 0) {
                str4 = m33.f26;
            }
            return sb.toString();
        }
        str4 = m33.f24;
        sb.append(str4);
        return sb.toString();
    }

    public static String sendReportBridgeHttps(String str, String str2, byte[] bArr) {
        HashMap hashMap;
        String str3;
        String str4;
        if (C0007.m30(str2)) {
            hashMap = new HashMap();
            hashMap.put("keyindex", str2);
        } else {
            hashMap = null;
        }
        C0008 m33 = C0009.m33(str, hashMap, bArr, null, 11000, 11000);
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(m33.f25));
        sb.append("|");
        int i = m33.f25;
        if (i != 200) {
            if (i == 1002 && (str3 = m33.f26) != null && str3.length() > 0) {
                str4 = m33.f26;
            }
            return sb.toString();
        }
        str4 = m33.f24;
        sb.append(str4);
        return sb.toString();
    }

    public static int windVaneCallBackAvailableBridge() {
        if (f61 == 0) {
            synchronized (DataReportJniBridge.class) {
                if (f61 == 0) {
                    try {
                        f63 = Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.WindvaneAdapter");
                        Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.WindvaneListener");
                        f65 = f63.getMethod("registerWindVaneListener", Context.class);
                        f60 = 1;
                    } catch (ClassNotFoundException | NoSuchMethodException unused) {
                    }
                    f61 = 1;
                }
            }
        }
        return f60;
    }
}
