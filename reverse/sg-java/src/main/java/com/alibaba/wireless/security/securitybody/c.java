package com.alibaba.wireless.security.securitybody;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
class c {

    /* renamed from: a, reason: collision with root package name */
    private static TelephonyManager f33a = null;
    private static volatile String b = null;
    private static volatile String c = null;
    private static final int d = 2;
    private static int e;
    private static int f;
    private static Context g;

    c() {
    }

    public static String a() {
        String deviceId;
        if (c() && b == null && 2 > e) {
            try {
                TelephonyManager telephonyManager = f33a;
                if (telephonyManager != null && (deviceId = telephonyManager.getDeviceId()) != null && deviceId.length() != 0) {
                    b = deviceId;
                }
            } catch (Exception unused) {
            }
            e++;
        }
        return b;
    }

    public static void a(Context context) {
        if (context != null && g == null) {
            g = context;
        }
        if (context == null || f33a != null) {
            return;
        }
        try {
            f33a = (TelephonyManager) context.getSystemService("phone");
        } catch (Throwable unused) {
        }
    }

    public static String b() {
        String subscriberId;
        if (c() && c == null && 2 > f) {
            try {
                TelephonyManager telephonyManager = f33a;
                if (telephonyManager != null && (subscriberId = telephonyManager.getSubscriberId()) != null && subscriberId.length() != 0) {
                    c = subscriberId;
                }
            } catch (Exception unused) {
            }
            f++;
        }
        return c;
    }

    public static boolean c() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = g;
        return context != null && context.checkSelfPermission("android.permission.READ_PHONE_STATE") == 0;
    }
}
