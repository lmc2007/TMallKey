package com.alibaba.one.android.inner;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Base64;
import com.alibaba.one.sdk.a;
import java.io.File;
import java.lang.reflect.Method;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;
import org.json.JSONObject;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class DeviceInfoCapturerFull {

    /* renamed from: a, reason: collision with root package name */
    public static Context f5a = null;
    public static volatile boolean b = false;
    public static Object c = new Object();

    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:18:0x003e -> B:8:0x003f). Please report as a decompilation issue!!! */
    public static String a() {
        long j;
        String str;
        if (Build.VERSION.SDK_INT > 8) {
            PackageInfo packageInfo = f5a.getPackageManager().getPackageInfo(f5a.getPackageName(), 0);
            if (packageInfo != null) {
                j = packageInfo.firstInstallTime;
            }
            j = -1;
        } else {
            ApplicationInfo applicationInfo = f5a.getPackageManager().getApplicationInfo(f5a.getPackageName(), 0);
            if (applicationInfo != null && (str = applicationInfo.sourceDir) != null) {
                j = new File(str).lastModified();
            }
            j = -1;
        }
        if (j != -1) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Long.valueOf(j));
            } catch (Exception unused) {
            }
        }
        return null;
    }

    public static int audioRoute() {
        AudioManager audioManager;
        Context context = f5a;
        if (context == null || (audioManager = (AudioManager) context.getSystemService("audio")) == null) {
            return -1;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            AudioDeviceInfo[] devices = audioManager.getDevices(2);
            if (devices != null && devices[0] != null) {
                return devices[0].getType();
            }
        } else {
            if (audioManager.isWiredHeadsetOn()) {
                return 3;
            }
            if (audioManager.isBluetoothScoOn()) {
                return 7;
            }
            if (audioManager.isBluetoothA2dpOn()) {
                return 8;
            }
        }
        return -1;
    }

    public static String b() {
        Object invoke;
        try {
            Class<?> cls = Class.forName("com.alibaba.wireless.security.open.SecurityGuardManager");
            Method declaredMethod = cls.getDeclaredMethod("getInstance", Context.class);
            if (declaredMethod != null) {
                Object invoke2 = declaredMethod.invoke(cls, f5a);
                Method declaredMethod2 = cls.getDeclaredMethod("getStaticDataStoreComp", new Class[0]);
                if (invoke2 != null && declaredMethod2 != null && (invoke = declaredMethod2.invoke(invoke2, new Object[0])) != null) {
                    Method declaredMethod3 = invoke.getClass().getDeclaredMethod("getAppKeyByIndex", Integer.TYPE, String.class);
                    if (declaredMethod3 != null) {
                        return (String) declaredMethod3.invoke(invoke, 0, "");
                    }
                }
            }
        } catch (Exception unused) {
        }
        return null;
    }

    public static String c() {
        JSONObject jSONObject = new JSONObject();
        if (Build.VERSION.SDK_INT >= 16) {
            try {
                PackageInfo packageInfo = f5a.getPackageManager().getPackageInfo(f5a.getPackageName(), 4096);
                if (packageInfo != null && packageInfo.requestedPermissions != null && packageInfo.requestedPermissions.length != 0) {
                    for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
                        String str = packageInfo.requestedPermissions[i];
                        if (str.startsWith("android.permission.")) {
                            str = str.substring(19);
                        }
                        jSONObject.put(str, (packageInfo.requestedPermissionsFlags[i] & 2) != 0);
                    }
                }
                return "";
            } catch (Exception unused) {
            }
        }
        return jSONObject.toString();
    }

    public static String d() {
        int i;
        String str;
        KeyStore keyStore;
        String str2;
        String str3;
        Certificate[] certificateChain;
        int length;
        String str4;
        byte[] bArr = {110, 111, 53, 48, 54, 98, 51, 56, 50, 50, 119, 98};
        byte[] bArr2 = {65, 110, 100, 114, 111, 105, 100, 75, 101, 121, 83, 116, 111, 114, 101};
        byte[] bArr3 = {49, 46, 51, 46, 54, 46, 49, 46, 52, 46, 49, 46, 49, 49, 49, 50, 57, 46, 50, 46, 49, 46, 49, 55};
        KeyStore keyStore2 = null;
        if (f5a == null || (i = Build.VERSION.SDK_INT) < 24 || i < 28) {
            return null;
        }
        try {
            byte[] bArr4 = new byte[8];
            new Random().nextBytes(bArr4);
            String str5 = new String(bArr2);
            str = new String(bArr);
            try {
                str3 = new String(bArr3);
                keyStore = KeyStore.getInstance(str5);
                try {
                    keyStore.load(null);
                    if (keyStore.containsAlias(str)) {
                        keyStore.deleteEntry(str);
                    }
                    KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(str, 12);
                    builder.setAttestationChallenge(bArr4);
                    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", str5);
                    keyPairGenerator.initialize(builder.build());
                    keyPairGenerator.generateKeyPair();
                    certificateChain = keyStore.getCertificateChain(str);
                    length = certificateChain.length;
                } catch (Throwable unused) {
                    keyStore2 = keyStore;
                    keyStore = keyStore2;
                    str2 = "0e|";
                    if (str != null) {
                        try {
                            keyStore.deleteEntry(str);
                        } catch (Throwable unused2) {
                        }
                    }
                    return str2 == null ? str2 : str2;
                }
            } catch (Throwable unused3) {
            }
        } catch (Throwable unused4) {
            str = null;
            keyStore = null;
        }
        if (length < 2) {
            return "0f|";
        }
        for (int i2 = 1; i2 < length; i2++) {
            try {
                PublicKey publicKey = certificateChain[i2].getPublicKey();
                int i3 = i2 - 1;
                ((X509Certificate) certificateChain[i3]).checkValidity();
                certificateChain[i3].verify(publicKey);
            } catch (Throwable unused5) {
                str4 = "1";
            }
        }
        str4 = "0";
        int i4 = length - 1;
        try {
            ((X509Certificate) certificateChain[i4]).checkValidity();
            certificateChain[i4].verify(certificateChain[i4].getPublicKey());
        } catch (Throwable unused6) {
            str4 = "1";
        }
        str2 = str4 + "|" + Base64.encodeToString(((X509Certificate) certificateChain[0]).getExtensionValue(str3), 2);
        if (str != null && keyStore != null) {
            keyStore.deleteEntry(str);
        }
        if (str2 == null && str2.length() > 400) {
            return "0a|" + str2.length();
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:153:0x0263, code lost:
    
        if (r9 == null) goto L155;
     */
    /* JADX WARN: Code restructure failed: missing block: B:235:0x0298, code lost:
    
        if (r9.length() > 0) goto L172;
     */
    /* JADX WARN: Removed duplicated region for block: B:205:? A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:218:? A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:93:0x0329 A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:94:0x032c A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String doCommandForString(int r9) {
        /*
            Method dump skipped, instructions count: 984
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.android.inner.DeviceInfoCapturerFull.doCommandForString(int):java.lang.String");
    }

    public static String e() {
        String a2;
        Context context = f5a;
        JSONObject jSONObject = new JSONObject();
        try {
        } catch (Exception e) {
            try {
                jSONObject.put("f_exp", a.a(e));
            } catch (Exception unused) {
            }
        }
        if (context == null) {
            jSONObject.put("e", -1);
        } else if (Build.VERSION.SDK_INT > 26 && Build.VERSION.SDK_INT >= 28) {
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                if (keyStore.containsAlias("no506b3822wbTB")) {
                    keyStore.deleteEntry("no506b3822wbTB");
                }
                try {
                    byte[] a3 = a.a(context);
                    KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder("no506b3822wbTB", 12);
                    builder.setAttestationChallenge(a3);
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            Method declaredMethod = Class.forName("android.security.keystore.KeyGenParameterSpec$Builder").getDeclaredMethod("setDevicePropertiesAttestationIncluded", Boolean.TYPE);
                            if (declaredMethod != null) {
                                declaredMethod.setAccessible(true);
                                declaredMethod.invoke(builder, true);
                            }
                        } catch (Exception e2) {
                            jSONObject.put("31_e", -10);
                            jSONObject.put("31_exp", a.a(e2));
                        }
                    }
                    try {
                        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
                        keyPairGenerator.initialize(builder.build());
                        keyPairGenerator.generateKeyPair();
                        try {
                            Certificate[] certificateChain = keyStore.getCertificateChain("no506b3822wbTB");
                            if (certificateChain != null) {
                                jSONObject.put("s", certificateChain.length);
                                for (int i = 1; i < certificateChain.length; i++) {
                                    int i2 = i - 1;
                                    try {
                                        ((X509Certificate) certificateChain[i2]).checkValidity();
                                        certificateChain[i2].verify(certificateChain[i].getPublicKey());
                                        jSONObject.put("v", true);
                                    } catch (Throwable th) {
                                        jSONObject.put("v", false);
                                        jSONObject.put("v_exp", a.a(th));
                                    }
                                }
                                try {
                                    jSONObject.put("c0t", Base64.encodeToString(certificateChain[0].getEncoded(), 2));
                                } catch (Exception e3) {
                                    jSONObject.put("c0t_e", -8);
                                    jSONObject.put("c0t_exp", a.a(e3));
                                }
                                try {
                                    jSONObject.put("c1pk", Base64.encodeToString(certificateChain[1].getPublicKey().getEncoded(), 2));
                                } catch (Exception e4) {
                                    jSONObject.put("c1pk_e", -9);
                                    jSONObject.put("c1pk_exp", a.a(e4));
                                }
                                jSONObject.put("ret", true);
                                return jSONObject.toString();
                            }
                            jSONObject.put("e", -7);
                        } catch (Exception e5) {
                            jSONObject.put("e", -6);
                            a2 = a.a(e5);
                            jSONObject.put("exp", a2);
                            return jSONObject.toString();
                        }
                    } catch (Exception e6) {
                        jSONObject.put("e", -5);
                        a2 = a.a(e6);
                    }
                } catch (Exception e7) {
                    jSONObject.put("e", -4);
                    a2 = a.a(e7);
                }
            } catch (Exception e8) {
                jSONObject.put("e", -3);
                a2 = a.a(e8);
            }
        } else {
            jSONObject.put("e", -2);
        }
        return jSONObject.toString();
    }

    public static Locale f() {
        try {
            Locale locale = Build.VERSION.SDK_INT >= 24 ? f5a.getResources().getConfiguration().getLocales().get(0) : f5a.getResources().getConfiguration().locale;
            if (locale != null) {
                return locale;
            }
            try {
                return Locale.getDefault();
            } catch (Throwable unused) {
                return locale;
            }
        } catch (Throwable unused2) {
            return null;
        }
    }

    public static String getAudioVolume() {
        Context context = f5a;
        if (context == null) {
            return null;
        }
        AudioManager audioManager = (AudioManager) context.getSystemService("audio");
        return String.valueOf(audioManager.getStreamVolume(3) / audioManager.getStreamMaxVolume(3));
    }

    public static String getUid(String str) {
        Context context = f5a;
        if (context == null) {
            return null;
        }
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(str, 128);
            if (applicationInfo != null) {
                return String.valueOf(applicationInfo.uid);
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    public static void initialize(Context context) {
        f5a = context;
    }
}
