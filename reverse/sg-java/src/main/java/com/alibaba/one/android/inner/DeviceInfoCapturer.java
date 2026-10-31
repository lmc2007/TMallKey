package com.alibaba.one.android.inner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.SensorManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import com.alibaba.one.sdk.c;
import com.alibaba.one.sdk.e;
import com.alibaba.one.sdk.j;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint({"MissingPermission"})
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class DeviceInfoCapturer {

    /* renamed from: a, reason: collision with root package name */
    public static Context f4a;
    public static TelephonyManager b;
    public static volatile String c;
    public static volatile String d;
    public static Map<Integer, String> e = new HashMap();
    public static Map<Integer, Integer> f = new HashMap();
    public static final List<Integer> g = Arrays.asList(0, 1, 18, 11, 15, 12);

    /* JADX WARN: Removed duplicated region for block: B:20:0x0057  */
    /* JADX WARN: Removed duplicated region for block: B:22:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String a(java.lang.String r4) {
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
            java.io.FileInputStream r1 = new java.io.FileInputStream     // Catch: java.lang.Throwable -> L41
            java.io.File r2 = new java.io.File     // Catch: java.lang.Throwable -> L41
            r2.<init>(r4)     // Catch: java.lang.Throwable -> L41
            r1.<init>(r2)     // Catch: java.lang.Throwable -> L41
            java.io.BufferedReader r4 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L3f
            java.io.InputStreamReader r2 = new java.io.InputStreamReader     // Catch: java.lang.Throwable -> L3f
            r2.<init>(r1)     // Catch: java.lang.Throwable -> L3f
            r3 = 256(0x100, float:3.59E-43)
            r4.<init>(r2, r3)     // Catch: java.lang.Throwable -> L3f
            java.lang.String r2 = r4.readLine()     // Catch: java.lang.Throwable -> L3d
            r4.close()     // Catch: java.io.IOException -> L37
        L37:
            r1.close()     // Catch: java.io.IOException -> L3b
            goto L50
        L3b:
            goto L50
        L3d:
            goto L43
        L3f:
            r4 = r0
            goto L43
        L41:
            r4 = r0
            r1 = r4
        L43:
            if (r4 == 0) goto L4a
            r4.close()     // Catch: java.io.IOException -> L49
            goto L4a
        L49:
        L4a:
            if (r1 == 0) goto L4f
            r1.close()     // Catch: java.io.IOException -> L4f
        L4f:
            r2 = r0
        L50:
            boolean r4 = c(r2)
            if (r4 != 0) goto L57
            goto L58
        L57:
            r0 = r2
        L58:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.DeviceInfoCapturer.a(java.lang.String):java.lang.String");
    }

    public static String a(boolean z) {
        WindowManager windowManager = (WindowManager) f4a.getSystemService("window");
        DisplayMetrics displayMetrics = new DisplayMetrics();
        if (!z) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        } else if (Build.VERSION.SDK_INT >= 17) {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        }
        return displayMetrics.widthPixels + "*" + displayMetrics.heightPixels;
    }

    public static /* synthetic */ boolean a() {
        return false;
    }

    /* JADX WARN: Removed duplicated region for block: B:8:0x001f A[Catch: all -> 0x003f, TryCatch #1 {all -> 0x003f, blocks: (B:3:0x0001, B:6:0x0015, B:8:0x001f, B:10:0x0028, B:12:0x002c, B:14:0x0031, B:25:0x0011, B:22:0x000b), top: B:2:0x0001, inners: #0 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String b() {
        /*
            r0 = 0
            android.content.Context r1 = com.alibaba.one.android.inner.DeviceInfoCapturer.f4a     // Catch: java.lang.Throwable -> L3f
            android.content.pm.PackageManager r1 = r1.getPackageManager()     // Catch: java.lang.Throwable -> L3f
            android.content.Context r2 = com.alibaba.one.android.inner.DeviceInfoCapturer.f4a     // Catch: java.lang.Throwable -> L3f
            if (r2 == 0) goto L14
            java.lang.String r2 = r2.getPackageName()     // Catch: java.lang.Throwable -> L10
            goto L15
        L10:
            r2 = move-exception
            r2.printStackTrace()     // Catch: java.lang.Throwable -> L3f
        L14:
            r2 = r0
        L15:
            r3 = 64
            android.content.pm.PackageInfo r1 = r1.getPackageInfo(r2, r3)     // Catch: java.lang.Throwable -> L3f
            com.alibaba.one.sdk.h r2 = com.alibaba.one.sdk.h.f14a     // Catch: java.lang.Throwable -> L3f
            if (r2 != 0) goto L26
            com.alibaba.one.sdk.h r2 = new com.alibaba.one.sdk.h     // Catch: java.lang.Throwable -> L3f
            r2.<init>()     // Catch: java.lang.Throwable -> L3f
            com.alibaba.one.sdk.h.f14a = r2     // Catch: java.lang.Throwable -> L3f
        L26:
            if (r1 == 0) goto L43
            android.content.pm.Signature[] r2 = r1.signatures     // Catch: java.lang.Throwable -> L3f
            if (r2 == 0) goto L43
            android.content.pm.Signature[] r2 = r1.signatures     // Catch: java.lang.Throwable -> L3f
            int r2 = r2.length     // Catch: java.lang.Throwable -> L3f
            if (r2 <= 0) goto L43
            android.content.pm.Signature[] r1 = r1.signatures     // Catch: java.lang.Throwable -> L3f
            r2 = 0
            r1 = r1[r2]     // Catch: java.lang.Throwable -> L3f
            java.lang.String r1 = r1.toCharsString()     // Catch: java.lang.Throwable -> L3f
            java.lang.String r0 = com.alibaba.one.sdk.h.a(r1)     // Catch: java.lang.Throwable -> L3f
            goto L43
        L3f:
            r1 = move-exception
            r1.printStackTrace()
        L43:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.DeviceInfoCapturer.b():java.lang.String");
    }

    public static String b(String str) {
        String str2;
        NetworkInterface byName;
        byte[] hardwareAddress;
        if (str == null) {
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
                sb.append(String.format("%02X:", Byte.valueOf(b2)));
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            str2 = sb.toString();
            if (c(str2)) {
                return str2;
            }
            return null;
        }
        return null;
    }

    /* JADX WARN: Code restructure failed: missing block: B:19:0x0030, code lost:
    
        r1 = r3[1].trim();
     */
    /* JADX WARN: Code restructure failed: missing block: B:26:0x0036, code lost:
    
        r2.close();
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String c() {
        /*
            java.lang.String r0 = "/proc/cpuinfo"
            r1 = 0
            java.io.FileReader r2 = new java.io.FileReader     // Catch: java.lang.Throwable -> L41
            r2.<init>(r0)     // Catch: java.lang.Throwable -> L41
            java.io.BufferedReader r0 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L3f
            r3 = 8192(0x2000, float:1.148E-41)
            r0.<init>(r2, r3)     // Catch: java.lang.Throwable -> L3f
        Lf:
            java.lang.String r3 = r0.readLine()     // Catch: java.lang.Throwable -> L3d
            if (r3 == 0) goto L36
            int r4 = r3.length()     // Catch: java.lang.Throwable -> L3d
            if (r4 <= 0) goto Lf
            java.lang.String r4 = ":"
            java.lang.String[] r3 = r3.split(r4)     // Catch: java.lang.Throwable -> L3d
            int r4 = r3.length     // Catch: java.lang.Throwable -> L3d
            r5 = 1
            if (r4 <= r5) goto Lf
            r4 = 0
            r4 = r3[r4]     // Catch: java.lang.Throwable -> L3d
            java.lang.String r6 = "BogoMIPS"
            boolean r4 = r4.contains(r6)     // Catch: java.lang.Throwable -> L3d
            if (r4 == 0) goto Lf
            r1 = r3[r5]     // Catch: java.lang.Throwable -> L3d
            java.lang.String r1 = r1.trim()     // Catch: java.lang.Throwable -> L3d
        L36:
            r2.close()     // Catch: java.lang.Throwable -> L39
        L39:
            r0.close()     // Catch: java.lang.Throwable -> L3c
        L3c:
            return r1
        L3d:
            r1 = r0
            goto L42
        L3f:
            goto L42
        L41:
            r2 = r1
        L42:
            if (r2 == 0) goto L49
            r2.close()     // Catch: java.lang.Throwable -> L48
            goto L49
        L48:
        L49:
            if (r1 == 0) goto L4e
            r1.close()     // Catch: java.lang.Throwable -> L4e
        L4e:
            java.lang.String r0 = ""
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.DeviceInfoCapturer.c():java.lang.String");
    }

    public static boolean c(String str) {
        return (str == null || str.equalsIgnoreCase("02:00:00:00:00:00") || str.equalsIgnoreCase("00:00:00:00:00:00")) ? false : true;
    }

    /* JADX WARN: Code restructure failed: missing block: B:25:0x0031, code lost:
    
        if (r3.contains("?") == false) goto L11;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String d() {
        /*
            java.lang.String r0 = "getUtdid"
            java.lang.String r1 = ""
            android.content.Context r2 = com.alibaba.one.android.inner.DeviceInfoCapturer.f4a
            java.lang.String r3 = "com.ut.device.UTDevice"
            java.lang.Class r3 = java.lang.Class.forName(r3)     // Catch: java.lang.Exception -> L58
            r4 = 1
            java.lang.Class[] r5 = new java.lang.Class[r4]     // Catch: java.lang.Exception -> L58
            java.lang.Class<android.content.Context> r6 = android.content.Context.class
            r7 = 0
            r5[r7] = r6     // Catch: java.lang.Exception -> L58
            java.lang.reflect.Method r3 = r3.getMethod(r0, r5)     // Catch: java.lang.Exception -> L58
            java.lang.Object[] r5 = new java.lang.Object[r4]     // Catch: java.lang.Exception -> L58
            r5[r7] = r2     // Catch: java.lang.Exception -> L58
            r6 = 0
            java.lang.Object r3 = r3.invoke(r6, r5)     // Catch: java.lang.Exception -> L58
            java.lang.String r3 = (java.lang.String) r3     // Catch: java.lang.Exception -> L58
            java.lang.String r5 = "?"
            if (r3 == 0) goto L36
            boolean r8 = r3.isEmpty()     // Catch: java.lang.Exception -> L34
            if (r8 != 0) goto L36
            boolean r8 = r3.contains(r5)     // Catch: java.lang.Exception -> L34
            if (r8 == 0) goto L34
            goto L36
        L34:
            r1 = r3
            goto L58
        L36:
            java.lang.String r8 = "com.ta.utdid2.device.UTDevice"
            java.lang.Class r8 = java.lang.Class.forName(r8)     // Catch: java.lang.Exception -> L34
            java.lang.Class[] r9 = new java.lang.Class[r4]     // Catch: java.lang.Exception -> L34
            java.lang.Class<android.content.Context> r10 = android.content.Context.class
            r9[r7] = r10     // Catch: java.lang.Exception -> L34
            java.lang.reflect.Method r0 = r8.getMethod(r0, r9)     // Catch: java.lang.Exception -> L34
            java.lang.Object[] r4 = new java.lang.Object[r4]     // Catch: java.lang.Exception -> L34
            r4[r7] = r2     // Catch: java.lang.Exception -> L34
            java.lang.Object r0 = r0.invoke(r6, r4)     // Catch: java.lang.Exception -> L34
            java.lang.String r0 = (java.lang.String) r0     // Catch: java.lang.Exception -> L34
            boolean r2 = r0.contains(r5)     // Catch: java.lang.Exception -> L57
            if (r2 == 0) goto L57
            goto L58
        L57:
            r1 = r0
        L58:
            return r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.DeviceInfoCapturer.d():java.lang.String");
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code restructure failed: missing block: B:103:0x0187, code lost:
    
        if (r0 != null) goto L284;
     */
    /* JADX WARN: Code restructure failed: missing block: B:113:0x01bd, code lost:
    
        if (r10 != null) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:114:0x01c2, code lost:
    
        if (r10 == null) goto L106;
     */
    /* JADX WARN: Code restructure failed: missing block: B:123:0x020f, code lost:
    
        if (r3.length() != 0) goto L257;
     */
    /* JADX WARN: Code restructure failed: missing block: B:150:0x02a6, code lost:
    
        if (r1 == null) goto L257;
     */
    /* JADX WARN: Code restructure failed: missing block: B:197:0x02fb, code lost:
    
        if (r2 == null) goto L193;
     */
    /* JADX WARN: Code restructure failed: missing block: B:212:0x035e, code lost:
    
        if (r3 != null) goto L257;
     */
    /* JADX WARN: Code restructure failed: missing block: B:222:0x037a, code lost:
    
        if (r0.checkSelfPermission("android.permission.READ_PHONE_STATE") != 0) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:228:0x038e, code lost:
    
        if (r3.length() == 0) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:234:0x039d, code lost:
    
        if (r0.checkSelfPermission("android.permission.READ_PHONE_STATE") != 0) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:240:0x03b1, code lost:
    
        if (r3.length() == 0) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:246:0x03c0, code lost:
    
        if (r0.checkSelfPermission("android.permission.READ_PHONE_STATE") != 0) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:252:0x03d4, code lost:
    
        if (r3.length() == 0) goto L18;
     */
    /* JADX WARN: Code restructure failed: missing block: B:93:0x01a8, code lost:
    
        if (r3 != null) goto L257;
     */
    /* JADX WARN: Removed duplicated region for block: B:180:0x0307  */
    /* JADX WARN: Removed duplicated region for block: B:181:0x0309 A[Catch: all -> 0x030f, TRY_LEAVE, TryCatch #20 {all -> 0x030f, blocks: (B:48:0x00d4, B:145:0x02a8, B:178:0x0301, B:181:0x0309), top: B:16:0x0057 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String doCommandForString(int r11) {
        /*
            Method dump skipped, instructions count: 1072
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.DeviceInfoCapturer.doCommandForString(int):java.lang.String");
    }

    public static void initialize(Context context) {
        f4a = context;
        try {
            context.getApplicationContext().getClassLoader();
            b = (TelephonyManager) context.getSystemService("phone");
            Context context2 = f4a;
            if (context2 != null && j.f16a == null) {
                j.b = context2;
                j.f16a = (SensorManager) context2.getSystemService("sensor");
            }
        } catch (Throwable unused) {
        }
        Context context3 = f4a;
        if (context3 != null) {
            try {
                if (("Huawei".equalsIgnoreCase(Build.BRAND) || "honor".equalsIgnoreCase(Build.BRAND)) && Build.VERSION.SDK_INT > 24) {
                    new Thread(new c(context3)).start();
                }
            } catch (Throwable unused2) {
            }
        }
        Context context4 = f4a;
        if (context4 != null) {
            try {
                if ("Oppo".equalsIgnoreCase(Build.BRAND) || "Realme".equalsIgnoreCase(Build.BRAND) || "OnePlus".equalsIgnoreCase(Build.BRAND)) {
                    new Thread(new e(context4)).start();
                }
            } catch (Throwable unused3) {
            }
        }
        UserTrackBridge.init(f4a);
    }
}
