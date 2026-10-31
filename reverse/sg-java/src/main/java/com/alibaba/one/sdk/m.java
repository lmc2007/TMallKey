package com.alibaba.one.sdk;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import java.util.Comparator;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class m {

    /* renamed from: a, reason: collision with root package name */
    public static Context f21a;
    public static WifiManager b;

    public static class a implements Comparator<WifiConfiguration> {
        @Override // java.util.Comparator
        public int compare(WifiConfiguration wifiConfiguration, WifiConfiguration wifiConfiguration2) {
            return wifiConfiguration.networkId - wifiConfiguration2.networkId;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:29:0x0058  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String a() {
        /*
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            r0.<init>()
            int r1 = android.os.Build.VERSION.SDK_INT
            r2 = 0
            r3 = 23
            if (r1 >= r3) goto Ld
            goto L19
        Ld:
            android.content.Context r1 = com.alibaba.one.sdk.m.f21a
            if (r1 == 0) goto L1b
            java.lang.String r3 = "android.permission.ACCESS_WIFI_STATE"
            int r1 = r1.checkSelfPermission(r3)
            if (r1 != 0) goto L1b
        L19:
            r1 = 1
            goto L1c
        L1b:
            r1 = 0
        L1c:
            java.lang.String r3 = ""
            if (r1 != 0) goto L21
            return r3
        L21:
            android.net.wifi.WifiManager r1 = com.alibaba.one.sdk.m.b
            java.util.List r1 = r1.getConfiguredNetworks()
            if (r1 == 0) goto Lc1
            r4 = 18
            r5 = 0
            java.lang.Class<android.net.wifi.WifiConfiguration> r6 = android.net.wifi.WifiConfiguration.class
            java.lang.String r7 = "enterpriseConfig"
            java.lang.reflect.Field r6 = r6.getDeclaredField(r7)     // Catch: java.lang.Throwable -> L43
            int r7 = android.os.Build.VERSION.SDK_INT     // Catch: java.lang.Throwable -> L44
            if (r7 < r4) goto L44
            java.lang.Class<android.net.wifi.WifiEnterpriseConfig> r7 = android.net.wifi.WifiEnterpriseConfig.class
            java.lang.Class[] r8 = new java.lang.Class[r2]     // Catch: java.lang.Throwable -> L44
            java.lang.String r9 = "getIdentity"
            java.lang.reflect.Method r7 = r7.getMethod(r9, r8)     // Catch: java.lang.Throwable -> L44
            goto L45
        L43:
            r6 = r5
        L44:
            r7 = r5
        L45:
            com.alibaba.one.sdk.m$a r8 = new com.alibaba.one.sdk.m$a
            r8.<init>()
            java.util.Collections.sort(r1, r8)
            java.util.Iterator r8 = r1.iterator()
            r9 = 0
        L52:
            boolean r10 = r8.hasNext()
            if (r10 == 0) goto Lb6
            java.lang.Object r10 = r8.next()
            android.net.wifi.WifiConfiguration r10 = (android.net.wifi.WifiConfiguration) r10
            java.lang.String r11 = r10.SSID
            byte[] r11 = r11.getBytes()
            r12 = 2
            java.lang.String r11 = android.util.Base64.encodeToString(r11, r12)
            r0.append(r11)
            java.lang.String r11 = "|"
            r0.append(r11)
            int r13 = r10.networkId
            r0.append(r13)
            if (r6 == 0) goto La8
            if (r7 == 0) goto La8
            int r13 = android.os.Build.VERSION.SDK_INT     // Catch: java.lang.Throwable -> La7
            if (r13 < r4) goto L85
            java.lang.Object r10 = r6.get(r10)     // Catch: java.lang.Throwable -> La7
            android.net.wifi.WifiEnterpriseConfig r10 = (android.net.wifi.WifiEnterpriseConfig) r10     // Catch: java.lang.Throwable -> La7
            goto L86
        L85:
            r10 = r5
        L86:
            if (r10 == 0) goto La8
            java.lang.Object[] r13 = new java.lang.Object[r2]     // Catch: java.lang.Throwable -> La7
            java.lang.Object r10 = r7.invoke(r10, r13)     // Catch: java.lang.Throwable -> La7
            java.lang.String r10 = (java.lang.String) r10     // Catch: java.lang.Throwable -> La7
            if (r10 == 0) goto La8
            boolean r13 = r10.equals(r3)     // Catch: java.lang.Throwable -> La7
            if (r13 != 0) goto La8
            r0.append(r11)     // Catch: java.lang.Throwable -> La7
            byte[] r10 = r10.getBytes()     // Catch: java.lang.Throwable -> La7
            java.lang.String r10 = android.util.Base64.encodeToString(r10, r12)     // Catch: java.lang.Throwable -> La7
            r0.append(r10)     // Catch: java.lang.Throwable -> La7
            goto La8
        La7:
        La8:
            java.lang.String r10 = "&"
            r0.append(r10)
            int r10 = r9 + 1
            r11 = 20
            if (r9 < r11) goto Lb4
            goto Lb6
        Lb4:
            r9 = r10
            goto L52
        Lb6:
            int r1 = r1.size()
            r0.append(r1)
            java.lang.String r3 = r0.toString()
        Lc1:
            return r3
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.sdk.m.a():java.lang.String");
    }
}
