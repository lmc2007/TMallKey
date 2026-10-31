package com.alibaba.one.sdk;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.io.File;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class g {

    /* renamed from: a, reason: collision with root package name */
    public static Context f13a;
    public static String b;
    public static String c;
    public static String d;
    public static long e;

    public static String a() {
        int i;
        int i2;
        Context context = f13a;
        if (context == null) {
            return "";
        }
        try {
            Display defaultDisplay = ((WindowManager) context.getSystemService("window")).getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            defaultDisplay.getMetrics(displayMetrics);
            float f = displayMetrics.xdpi;
            float f2 = displayMetrics.ydpi;
            int i3 = Build.VERSION.SDK_INT;
            if (i3 < 14 || i3 >= 17) {
                if (i3 >= 17) {
                    defaultDisplay.getRealMetrics(displayMetrics);
                }
                i = displayMetrics.widthPixels;
                i2 = displayMetrics.heightPixels;
            } else {
                Method method = Display.class.getMethod("getRawWidth", new Class[0]);
                Method method2 = Display.class.getMethod("getRawHeight", new Class[0]);
                int intValue = ((Integer) method.invoke(defaultDisplay, new Object[0])).intValue();
                int intValue2 = ((Integer) method2.invoke(defaultDisplay, new Object[0])).intValue();
                i = intValue;
                i2 = intValue2;
            }
            double d2 = i;
            double d3 = f;
            Double.isNaN(d2);
            Double.isNaN(d3);
            double d4 = d2 / d3;
            double d5 = i2;
            double d6 = f2;
            Double.isNaN(d5);
            Double.isNaN(d6);
            return new DecimalFormat("#.##").format(d4) + "*" + new DecimalFormat("#.##").format(d5 / d6) + "*" + Float.toString(defaultDisplay.getRefreshRate());
        } catch (Throwable unused) {
            return "";
        }
    }

    public static String a(File file) {
        if (file == null) {
            return null;
        }
        StatFs statFs = new StatFs(file.getPath());
        if (Build.VERSION.SDK_INT >= 18) {
            try {
                Long l = (Long) statFs.getClass().getDeclaredMethod("getAvailableBytes", new Class[0]).invoke(statFs, new Object[0]);
                if (l != null) {
                    return l.toString();
                }
                return null;
            } catch (Throwable unused) {
                return null;
            }
        }
        long availableBlocks = statFs.getAvailableBlocks();
        return (statFs.getBlockCount() * availableBlocks) + "";
    }

    public static synchronized void a(int i) {
        Intent registerReceiver;
        synchronized (g.class) {
            try {
                long currentTimeMillis = System.currentTimeMillis();
                if (e == 0 || currentTimeMillis - e >= 10000 || ((i != 1 || b == null) && ((i != 2 || c == null) && (i != 3 || d == null)))) {
                    e = currentTimeMillis;
                    Context context = f13a;
                    if (context != null && (registerReceiver = context.registerReceiver(null, new IntentFilter("android.intent.action.BATTERY_CHANGED"))) != null) {
                        b = registerReceiver.getIntExtra("level", -1) + "";
                        c = registerReceiver.getIntExtra("voltage", -1) + "";
                        d = registerReceiver.getIntExtra("temperature", -1) + "";
                    }
                }
            } catch (Throwable unused) {
            }
        }
    }

    public static boolean a(Context context) {
        try {
            return context.getPackageManager().hasSystemFeature("android.hardware.type.television");
        } catch (Exception unused) {
            return false;
        }
    }

    public static String b() {
        Context context = f13a;
        if (context == null) {
            return "";
        }
        try {
            new DisplayMetrics();
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            int i = displayMetrics.widthPixels;
            int i2 = displayMetrics.heightPixels;
            return String.valueOf(Math.min(i, i2)) + "*" + String.valueOf(Math.max(i, i2));
        } catch (Exception unused) {
            return "";
        }
    }

    public static boolean b(Context context) {
        return ((UiModeManager) context.getSystemService("uimode")).getCurrentModeType() == 4;
    }

    public static boolean c(Context context) {
        return (context.getResources().getConfiguration().screenLayout & 15) >= 3;
    }
}
