package com.taobao.wireless.security.adapter.datacollection;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

/* renamed from: com.taobao.wireless.security.adapter.datacollection.г, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0014 {

    /* renamed from: а, reason: contains not printable characters */
    private static TelephonyManager f46;

    /* renamed from: б, reason: contains not printable characters */
    private static volatile String f47;

    /* renamed from: в, reason: contains not printable characters */
    private static volatile String f48;

    /* renamed from: г, reason: contains not printable characters */
    private static int f49;

    /* renamed from: д, reason: contains not printable characters */
    private static int f50;

    /* renamed from: е, reason: contains not printable characters */
    private static Context f51;

    /* renamed from: а, reason: contains not printable characters */
    public static String m60() {
        String deviceId;
        if (m63() && f47 == null && 2 > f49) {
            try {
                TelephonyManager telephonyManager = f46;
                if (telephonyManager != null && (deviceId = telephonyManager.getDeviceId()) != null && deviceId.length() != 0) {
                    f47 = deviceId;
                }
            } catch (Exception unused) {
            }
            f49++;
        }
        return f47;
    }

    /* renamed from: а, reason: contains not printable characters */
    public static synchronized void m61(Context context) {
        synchronized (C0014.class) {
            if (context != null) {
                try {
                    if (f51 == null) {
                        f51 = context;
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
            if (context != null) {
                if (f46 == null) {
                    try {
                        f46 = (TelephonyManager) context.getSystemService("phone");
                    } catch (Throwable unused) {
                    }
                }
            }
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    public static String m62() {
        String subscriberId;
        if (m63() && f48 == null && 2 > f50) {
            try {
                TelephonyManager telephonyManager = f46;
                if (telephonyManager != null && (subscriberId = telephonyManager.getSubscriberId()) != null && subscriberId.length() != 0) {
                    f48 = subscriberId;
                }
            } catch (Exception unused) {
            }
            f50++;
        }
        return f48;
    }

    /* renamed from: в, reason: contains not printable characters */
    public static boolean m63() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = f51;
        return context != null && context.checkSelfPermission("android.permission.READ_PHONE_STATE") == 0;
    }
}
