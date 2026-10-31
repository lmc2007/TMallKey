package com.taobao.wireless.security.adapter.datacollection;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.net.NetworkInterface;

/* renamed from: com.taobao.wireless.security.adapter.datacollection.е, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0016 {

    /* renamed from: а, reason: contains not printable characters */
    private static Context f53;

    /* renamed from: б, reason: contains not printable characters */
    private static WifiManager f54;

    /* JADX WARN: Removed duplicated region for block: B:11:? A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:8:0x0014  */
    /* renamed from: а, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String m66() {
        /*
            r0 = 0
            android.net.wifi.WifiInfo r1 = m71()     // Catch: java.lang.Throwable -> Lc
            if (r1 == 0) goto Lc
            java.lang.String r1 = r1.getMacAddress()     // Catch: java.lang.Throwable -> Lc
            goto Ld
        Lc:
            r1 = r0
        Ld:
            boolean r2 = m72(r1)
            if (r2 != 0) goto L14
            goto L15
        L14:
            r0 = r1
        L15:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.datacollection.C0016.m66():java.lang.String");
    }

    /* JADX WARN: Removed duplicated region for block: B:20:0x0055  */
    /* JADX WARN: Removed duplicated region for block: B:22:? A[RETURN, SYNTHETIC] */
    /* renamed from: а, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String m67(java.lang.String r4) {
        /*
            r0 = 0
            if (r4 != 0) goto L4
            return r0
        L4:
            java.lang.StringBuilder r1 = new java.lang.StringBuilder
            r1.<init>()
            java.lang.String r2 = "/sys/class/net/"
            r1.append(r2)
            r1.append(r4)
            java.lang.String r4 = "/address"
            r1.append(r4)
            java.lang.String r4 = r1.toString()
            java.io.FileInputStream r1 = new java.io.FileInputStream     // Catch: java.lang.Throwable -> L3f
            java.io.File r2 = new java.io.File     // Catch: java.lang.Throwable -> L3f
            r2.<init>(r4)     // Catch: java.lang.Throwable -> L3f
            r1.<init>(r2)     // Catch: java.lang.Throwable -> L3f
            java.io.BufferedReader r4 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L3d
            java.io.InputStreamReader r2 = new java.io.InputStreamReader     // Catch: java.lang.Throwable -> L3d
            r2.<init>(r1)     // Catch: java.lang.Throwable -> L3d
            r3 = 256(0x100, float:3.59E-43)
            r4.<init>(r2, r3)     // Catch: java.lang.Throwable -> L3d
            java.lang.String r2 = r4.readLine()     // Catch: java.lang.Throwable -> L41
            r4.close()     // Catch: java.io.IOException -> L37
        L37:
            r1.close()     // Catch: java.io.IOException -> L3b
            goto L4e
        L3b:
            goto L4e
        L3d:
            r4 = r0
            goto L41
        L3f:
            r4 = r0
            r1 = r4
        L41:
            if (r4 == 0) goto L48
            r4.close()     // Catch: java.io.IOException -> L47
            goto L48
        L47:
        L48:
            if (r1 == 0) goto L4d
            r1.close()     // Catch: java.io.IOException -> L4d
        L4d:
            r2 = r0
        L4e:
            boolean r4 = m72(r2)
            if (r4 != 0) goto L55
            goto L56
        L55:
            r0 = r2
        L56:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.datacollection.C0016.m67(java.lang.String):java.lang.String");
    }

    /* renamed from: а, reason: contains not printable characters */
    public static void m68(Context context) {
        if (context != null && f53 == null) {
            f53 = context;
        }
        if (context == null || f54 != null) {
            return;
        }
        try {
            f54 = (WifiManager) context.getSystemService("wifi");
        } catch (Throwable unused) {
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    public static String m69() {
        String str;
        try {
            for (String str2 : new String[]{"wlan0", "eth1", "eth0"}) {
                String m70 = m70(str2);
                if (m70 != null) {
                    return m70;
                }
                String m67 = m67(str2);
                if (m67 != null) {
                    return m67;
                }
            }
            str = m66();
            if (str != null) {
                return str;
            }
        } catch (Throwable unused) {
            str = null;
        }
        return str == null ? "" : str.toLowerCase();
    }

    /* renamed from: б, reason: contains not printable characters */
    private static String m70(String str) {
        String str2;
        NetworkInterface byName;
        byte[] hardwareAddress;
        if (str == null || !m73()) {
            return null;
        }
        try {
            byName = NetworkInterface.getByName(str);
        } catch (Throwable unused) {
            str2 = null;
        }
        if (byName != null && (hardwareAddress = byName.getHardwareAddress()) != null && hardwareAddress.length != 0) {
            StringBuilder sb = new StringBuilder();
            for (byte b : hardwareAddress) {
                sb.append(String.format("%02x:", Byte.valueOf(b)));
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            str2 = sb.toString();
            if (m72(str2)) {
                return str2;
            }
            return null;
        }
        return null;
    }

    /* renamed from: в, reason: contains not printable characters */
    private static WifiInfo m71() {
        WifiManager wifiManager;
        if (!m74() || (wifiManager = f54) == null) {
            return null;
        }
        return wifiManager.getConnectionInfo();
    }

    /* renamed from: в, reason: contains not printable characters */
    public static boolean m72(String str) {
        return (str == null || str.equalsIgnoreCase("02:00:00:00:00:00") || str.equalsIgnoreCase("00:00:00:00:00:00")) ? false : true;
    }

    /* renamed from: г, reason: contains not printable characters */
    public static boolean m73() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = f53;
        return context != null && context.checkSelfPermission("android.permission.INTERNET") == 0;
    }

    /* renamed from: д, reason: contains not printable characters */
    public static boolean m74() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = f53;
        return context != null && context.checkSelfPermission("android.permission.ACCESS_WIFI_STATE") == 0;
    }
}
