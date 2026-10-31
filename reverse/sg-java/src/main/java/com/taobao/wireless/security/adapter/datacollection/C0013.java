package com.taobao.wireless.security.adapter.datacollection;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/* renamed from: com.taobao.wireless.security.adapter.datacollection.в, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0013 {

    /* renamed from: а, reason: contains not printable characters */
    private static String f44;

    /* renamed from: б, reason: contains not printable characters */
    private static PackageManager f45;

    /* renamed from: а, reason: contains not printable characters */
    public static String m56() {
        PackageInfo packageInfo;
        String str;
        try {
            if (f45 == null || f44 == null || (packageInfo = f45.getPackageInfo(f44, 0)) == null || (str = packageInfo.versionName) == null) {
                return null;
            }
            if (str.length() != 0) {
                return str;
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    public static void m57(Context context) {
        if (context == null || f45 != null) {
            return;
        }
        try {
            f44 = context.getPackageName();
        } catch (Throwable unused) {
        }
        try {
            f45 = context.getPackageManager();
        } catch (Throwable unused2) {
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    public static boolean m58(String str) {
        try {
            PackageManager packageManager = f45;
            if (packageManager != null) {
                return packageManager.hasSystemFeature(str);
            }
            return false;
        } catch (Exception unused) {
            return false;
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    public static String m59() {
        return f44;
    }
}
