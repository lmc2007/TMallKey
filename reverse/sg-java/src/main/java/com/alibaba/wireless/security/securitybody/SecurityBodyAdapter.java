package com.alibaba.wireless.security.securitybody;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.lang.reflect.Method;

@TargetApi(19)
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class SecurityBodyAdapter {

    /* renamed from: a, reason: collision with root package name */
    private static String f28a = "SecurityBodyAdapter";
    private static final int b = 0;
    private static final int c = 1;
    private static final int d = 2;
    private static final int e = 3;
    private static final int f = 4;
    private static final int g = 5;
    private static final int h = 6;
    private static final int i = 8;
    private static final int j = 9;
    private static final int k = 10;
    private static final int l = 11;
    private static final int m = 12;
    private static final int n = 13;
    private static Context o = null;
    private static Object p = null;
    private static boolean q = false;
    private static long r = 0;
    private static final int s = 1;
    private static final int t = 2;
    private static String u = null;
    private static boolean v = false;
    private static Class w;
    private static Method x;

    private static String a() {
        a(1);
        return v ? "1" : "0";
    }

    private static synchronized void a(int i2) {
        Intent registerReceiver;
        synchronized (SecurityBodyAdapter.class) {
            try {
                long currentTimeMillis = System.currentTimeMillis();
                boolean z = true;
                if (r == 0 || currentTimeMillis - r >= 10000 || ((i2 != 1 || u == null) && i2 != 2)) {
                    r = currentTimeMillis;
                    Context context = o;
                    if (context != null && (registerReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"))) != null) {
                        u = registerReceiver.getIntExtra("level", -1) + "";
                        int intExtra = registerReceiver.getIntExtra("status", 1);
                        if (intExtra != 2 && intExtra != g) {
                            z = false;
                        }
                        v = z;
                    }
                }
            } catch (Throwable unused) {
            }
        }
    }

    private static String b() {
        a(1);
        String str = u;
        return str == null ? "-1" : str;
    }

    private static String c() {
        Method method;
        Class cls = w;
        if (cls != null && (method = x) != null) {
            try {
                return String.valueOf(((Long) method.invoke(cls, new Object[0])).longValue());
            } catch (Exception unused) {
            }
        }
        return null;
    }

    private static String d() {
        try {
            int myPid = Process.myPid();
            if (o == null) {
                return "";
            }
            for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : ((ActivityManager) o.getSystemService("activity")).getRunningAppProcesses()) {
                if (runningAppProcessInfo.pid == myPid) {
                    return runningAppProcessInfo.processName != null ? runningAppProcessInfo.processName : "";
                }
            }
            return "";
        } catch (Throwable unused) {
            return "";
        }
    }

    public static String doAdapter(int i2) {
        if (!q) {
            synchronized (p) {
                if (!q) {
                    com.alibaba.wireless.security.securitybody.c.a(o);
                    com.alibaba.wireless.security.securitybody.d.a(o);
                    try {
                        w = Class.forName("mtopsdk.mtop.global.SDKUtils");
                        x = w.getMethod("getCorrectionTime", new Class[0]);
                    } catch (Exception unused) {
                    }
                    q = true;
                }
            }
        }
        if (i2 == 0) {
            return com.alibaba.wireless.security.securitybody.c.a();
        }
        if (i2 == 1) {
            return com.alibaba.wireless.security.securitybody.c.b();
        }
        if (i2 == 2) {
            return com.alibaba.wireless.security.securitybody.d.a();
        }
        if (i2 == g) {
            return g();
        }
        if (i2 == h) {
            return b();
        }
        switch (i2) {
            case i /* 8 */:
                return a();
            case j /* 9 */:
                return c();
            case k /* 10 */:
                return f();
            case l /* 11 */:
                return e();
            case m /* 12 */:
                return d();
            case n /* 13 */:
                return String.valueOf(LifeCycle.isViewChanged() ? 1 : 0);
            default:
                return null;
        }
    }

    private static String e() {
        WindowManager windowManager;
        Display defaultDisplay;
        Context context = o;
        if (context != null && (windowManager = (WindowManager) context.getSystemService("window")) != null && (defaultDisplay = windowManager.getDefaultDisplay()) != null) {
            try {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                if (Build.VERSION.SDK_INT >= 17) {
                    defaultDisplay.getRealMetrics(displayMetrics);
                    return String.format("%.2f", Double.valueOf(Math.sqrt(Math.pow(displayMetrics.widthPixels / displayMetrics.xdpi, 2.0d) + Math.pow(displayMetrics.heightPixels / displayMetrics.ydpi, 2.0d))));
                }
            } catch (Exception unused) {
            }
        }
        return null;
    }

    private static String f() {
        Context context = o;
        if (context == null) {
            return "0";
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.hardware.usb.action.USB_STATE");
        Intent registerReceiver = context.registerReceiver(null, intentFilter);
        return (registerReceiver == null || !registerReceiver.getBooleanExtra("connected", false)) ? "0" : "1";
    }

    private static String g() {
        try {
            return (String) Class.forName("com.taobao.login4android.Login").getMethod("getNick", new Class[0]).invoke(null, new Object[0]);
        } catch (Exception unused) {
            return null;
        }
    }

    public static void initialize(Context context) {
        o = context;
        p = new Object();
    }
}
