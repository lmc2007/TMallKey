package com.alibaba.one.android.inner;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class UserTrackBridge {

    /* renamed from: a, reason: collision with root package name */
    public static Context f6a;
    public static String b;
    public static long c = System.nanoTime();
    public static long d = System.currentTimeMillis();
    public static volatile int e = 0;
    public static volatile int f = 0;
    public static Class g = null;
    public static Class h = null;
    public static Class i = null;
    public static Constructor j = null;
    public static Method k = null;
    public static Method l = null;
    public static Method m = null;
    public static Method n = null;

    public static void addUtRecord(String str, String str2, String str3) {
        Map map;
        Object invoke;
        Object invoke2;
        if (isUtAvaiable() == 0 || str2 == null || str2.length() == 0) {
            return;
        }
        try {
            String valueOf = String.valueOf(str);
            HashMap hashMap = new HashMap();
            hashMap.put("pn", getProcessName());
            hashMap.put("pid", "" + Process.myPid());
            hashMap.put("ppid", "" + Process.myTid());
            hashMap.put("pa", "" + b);
            long nanoTime = (d * 1000) + ((System.nanoTime() - c) / 1000);
            hashMap.put("ct", "" + System.currentTimeMillis());
            hashMap.put("ctu", "" + nanoTime);
            String convertWithIteration = convertWithIteration(hashMap);
            HashMap hashMap2 = new HashMap();
            hashMap2.put("v", String.valueOf(str3));
            Object newInstance = j.newInstance("Page_EcoOneSDK", 19999, str2, valueOf, convertWithIteration, hashMap2);
            if (newInstance == null || (map = (Map) k.invoke(newInstance, new Object[0])) == null || map.size() == 0 || (invoke = l.invoke(h, new Object[0])) == null || (invoke2 = m.invoke(invoke, new Object[0])) == null) {
                return;
            }
            n.invoke(invoke2, map);
        } catch (Exception unused) {
        }
    }

    public static String convertWithIteration(Map<String, ?> map) {
        if (map == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String str : map.keySet()) {
            sb.append(str + "=" + map.get(str) + ",");
        }
        sb.delete(sb.length() - 1, sb.length());
        return sb.toString();
    }

    /* JADX WARN: Code restructure failed: missing block: B:31:0x0063, code lost:
    
        if (r2 == null) goto L26;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String getProcessName() {
        /*
            int r0 = android.os.Build.VERSION.SDK_INT
            r1 = 0
            r2 = 28
            if (r0 < r2) goto L1f
            java.lang.String r0 = "android.app.Application"
            java.lang.Class r0 = java.lang.Class.forName(r0)     // Catch: java.lang.Exception -> L1f
            java.lang.String r2 = "getProcessName"
            r3 = 0
            java.lang.Class[] r4 = new java.lang.Class[r3]     // Catch: java.lang.Exception -> L1f
            java.lang.reflect.Method r0 = r0.getDeclaredMethod(r2, r4)     // Catch: java.lang.Exception -> L1f
            java.lang.Object[] r2 = new java.lang.Object[r3]     // Catch: java.lang.Exception -> L1f
            java.lang.Object r0 = r0.invoke(r1, r2)     // Catch: java.lang.Exception -> L1f
            java.lang.String r0 = (java.lang.String) r0     // Catch: java.lang.Exception -> L1f
            return r0
        L1f:
            java.io.File r0 = new java.io.File
            java.lang.StringBuilder r2 = new java.lang.StringBuilder
            r2.<init>()
            java.lang.String r3 = "/proc/"
            r2.append(r3)
            int r3 = android.os.Process.myPid()
            r2.append(r3)
            java.lang.String r3 = "/cmdline"
            r2.append(r3)
            java.lang.String r2 = r2.toString()
            r0.<init>(r2)
            java.io.BufferedReader r2 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L5b java.lang.Exception -> L62
            java.io.FileReader r3 = new java.io.FileReader     // Catch: java.lang.Throwable -> L5b java.lang.Exception -> L62
            r3.<init>(r0)     // Catch: java.lang.Throwable -> L5b java.lang.Exception -> L62
            r2.<init>(r3)     // Catch: java.lang.Throwable -> L5b java.lang.Exception -> L62
            java.lang.String r0 = r2.readLine()     // Catch: java.lang.Throwable -> L56 java.lang.Exception -> L59
            if (r0 == 0) goto L65
            java.lang.String r0 = r0.trim()     // Catch: java.lang.Throwable -> L56 java.lang.Exception -> L59
            r2.close()     // Catch: java.io.IOException -> L55
        L55:
            return r0
        L56:
            r0 = move-exception
            r1 = r2
            goto L5c
        L59:
            goto L63
        L5b:
            r0 = move-exception
        L5c:
            if (r1 == 0) goto L61
            r1.close()     // Catch: java.io.IOException -> L61
        L61:
            throw r0
        L62:
            r2 = r1
        L63:
            if (r2 == 0) goto L68
        L65:
            r2.close()     // Catch: java.io.IOException -> L68
        L68:
            return r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.UserTrackBridge.getProcessName():java.lang.String");
    }

    public static void init(Context context) {
        ApplicationInfo applicationInfo;
        if (context != null) {
            f6a = context;
            if (context == null || (applicationInfo = context.getApplicationInfo()) == null) {
                return;
            }
            b = applicationInfo.packageName;
        }
    }

    public static int isUtAvaiable() {
        if (f == 0) {
            synchronized (UserTrackBridge.class) {
                if (f == 0) {
                    try {
                        g = Class.forName("com.ut.mini.internal.UTOriginalCustomHitBuilder");
                        h = Class.forName("com.ut.mini.UTAnalytics");
                        i = Class.forName("com.ut.mini.UTTracker");
                        j = g.getConstructor(String.class, Integer.TYPE, String.class, String.class, String.class, Map.class);
                        k = g.getMethod("build", new Class[0]);
                        l = h.getMethod("getInstance", new Class[0]);
                        m = h.getMethod("getDefaultTracker", new Class[0]);
                        n = i.getMethod("send", Map.class);
                        e = 1;
                    } catch (ClassNotFoundException | NoSuchMethodException unused) {
                    }
                    f = 1;
                }
            }
        }
        return e;
    }
}
