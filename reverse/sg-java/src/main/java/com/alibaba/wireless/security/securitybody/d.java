package com.alibaba.wireless.security.securitybody;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.net.NetworkInterface;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
class d {

    /* renamed from: a, reason: collision with root package name */
    private static Context f34a = null;
    private static WifiManager b = null;
    private static final String c = "02:00:00:00:00:00";
    private static final String d = "00:00:00:00:00:00";

    d() {
    }

    public static String a() {
        String str;
        try {
            str = a("wlan0");
            if (str != null) {
                return str;
            }
        } catch (Throwable unused) {
            str = null;
        }
        return str == null ? "" : str.toLowerCase();
    }

    private static String a(String str) {
        String str2;
        NetworkInterface byName;
        byte[] hardwareAddress;
        if (str == null || !b()) {
            return null;
        }
        try {
            byName = NetworkInterface.getByName(str);
        } catch (Throwable unused) {
            str2 = null;
        }
        if (byName != null && (hardwareAddress = byName.getHardwareAddress()) != null && hardwareAddress.length != 0) {
            StringBuilder sb = new StringBuilder();
            for (byte b2 : hardwareAddress) {
                sb.append(String.format("%02x:", Byte.valueOf(b2)));
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            str2 = sb.toString();
            if (b(str2)) {
                return str2;
            }
            return null;
        }
        return null;
    }

    public static void a(Context context) {
        if (context != null && f34a == null) {
            f34a = context;
        }
        if (context == null || b != null) {
            return;
        }
        try {
            b = (WifiManager) context.getSystemService("wifi");
        } catch (Throwable unused) {
        }
    }

    public static boolean b() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = f34a;
        return context != null && context.checkSelfPermission("android.permission.INTERNET") == 0;
    }

    public static boolean b(String str) {
        return (str == null || str.equalsIgnoreCase(c) || str.equalsIgnoreCase(d)) ? false : true;
    }
}
