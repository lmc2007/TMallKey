package com.alibaba.one.sdk;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Process;
import java.util.HashMap;
import java.util.Map;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class i {

    /* renamed from: a, reason: collision with root package name */
    public static volatile boolean f15a = false;
    public static final Object b = new Object();
    public static final Map<String, Integer> c = new HashMap();
    public static volatile String d = null;
    public static volatile PackageManager e = null;
    public static Context f = null;

    public static String a() {
        ApplicationInfo applicationInfo;
        try {
            PackageManager packageManager = e;
            if (packageManager != null && (applicationInfo = packageManager.getApplicationInfo(d, 0)) != null) {
                return packageManager.getApplicationLabel(applicationInfo).toString();
            }
        } catch (Exception unused) {
        }
        return null;
    }

    public static void a(Context context) {
        if (context == null || e != null) {
            return;
        }
        f = context;
        try {
            d = context.getPackageName();
        } catch (Throwable unused) {
        }
        try {
            e = context.getPackageManager();
        } catch (Throwable unused2) {
        }
    }

    public static boolean a(String str) {
        try {
            PackageManager packageManager = e;
            if (packageManager != null) {
                return packageManager.hasSystemFeature(str);
            }
            return false;
        } catch (Exception unused) {
            return false;
        }
    }

    public static String b() {
        FeatureInfo[] systemAvailableFeatures;
        try {
            if (!f15a) {
                synchronized (b) {
                    if (!f15a) {
                        String[] split = "audio.low_latency,bluetooth,bluetooth_le,camera,camera.autofocus,camera.any,camera.flash,camera.front,consumerir,location,location.gps,location.network,microphone,nfc,nfc.hce,nfc.hce,sensor.accelerometer,sensor.barometer,sensor.compass,sensor.gyroscope,sensor.light,sensor.proximity,sensor.stepcounter,sensor.stepdetector,telephony,telephony.cdma,telephony.gsm,usb.host,usb.accessory,touchscreen,touchscreen.multitouch,touchscreen.multitouch.distinct,touchscreen.multitouch.jazzhand,faketouch,faketouch.multitouch.distinct,faketouch.multitouch.jazzhand,screen.portrait,screen.landscape,wifi,wifi.direct,type.television".split(",");
                        int length = split.length;
                        for (int i = 0; i < length; i++) {
                            c.put("android.hardware." + split[i], Integer.valueOf(i));
                        }
                        String[] split2 = "sip,sip.voip,live_wallpaper,app_widgets,home_screen,input_methods,device_admin".split(",");
                        int length2 = split2.length;
                        for (int i2 = 0; i2 < length2; i2++) {
                            c.put("android.software." + split2[i2], Integer.valueOf(i2));
                        }
                        f15a = true;
                    }
                }
            }
            if (e != null && (systemAvailableFeatures = e.getSystemAvailableFeatures()) != null && systemAvailableFeatures.length != 0) {
                long j = 0;
                StringBuilder sb = new StringBuilder();
                for (FeatureInfo featureInfo : systemAvailableFeatures) {
                    Integer num = c.get(featureInfo.name);
                    if (num == null) {
                        sb.append(featureInfo.name == null ? "<null>" : featureInfo.name);
                        sb.append(",");
                    } else {
                        j |= 1 << num.intValue();
                    }
                }
                if (sb.length() == 0) {
                    return String.valueOf(j);
                }
                return String.valueOf(j) + "," + sb.toString();
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    public static String c() {
        Signature[] signatureArr;
        try {
            if (f != null && (signatureArr = f.getPackageManager().getPackageInfo("android", 64).signatures) != null && signatureArr.length > 0) {
                return h.a(signatureArr[0].toCharsString());
            }
        } catch (Exception unused) {
        }
        return null;
    }

    public static boolean d() {
        try {
            int myPid = Process.myPid();
            if (f == null) {
                return false;
            }
            for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : ((ActivityManager) f.getSystemService("activity")).getRunningAppProcesses()) {
                if (runningAppProcessInfo.pid == myPid) {
                    return runningAppProcessInfo.importance == 100;
                }
            }
            return false;
        } catch (Exception unused) {
            return false;
        }
    }

    public static boolean e() {
        PackageInfo packageInfo;
        try {
            if (e != null && d != null && (packageInfo = e.getPackageInfo(d, 0)) != null && (packageInfo.applicationInfo.flags & 1) != 0) {
                if ((packageInfo.applicationInfo.flags & 128) == 0) {
                    return true;
                }
            }
        } catch (Throwable unused) {
        }
        return false;
    }
}
