package com.taobao.wireless.security.adapter.common;

import android.content.Context;
import android.os.Build;
import com.alibaba.minilibc.android.HttpClientUtils;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;

/* renamed from: com.taobao.wireless.security.adapter.common.в, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0009 {

    /* renamed from: а, reason: contains not printable characters */
    private static Context f27;

    /* JADX WARN: Code restructure failed: missing block: B:61:0x00f4, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L61;
     */
    /* JADX WARN: Code restructure failed: missing block: B:62:0x00f6, code lost:
    
        android.net.TrafficStats.clearThreadStatsTag();
     */
    /* JADX WARN: Code restructure failed: missing block: B:75:0x015b, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L61;
     */
    /* JADX WARN: Code restructure failed: missing block: B:87:0x013b, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L61;
     */
    /* JADX WARN: Code restructure failed: missing block: B:99:0x011b, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L61;
     */
    /* JADX WARN: Not initialized variable reg: 5, insn: 0x0160: IF  (r5 I:??[int, boolean, OBJECT, ARRAY, byte, short, char]) == (0 ??[int, boolean, OBJECT, ARRAY, byte, short, char])  -> B:119:0x0165, block:B:118:0x0160 */
    /* JADX WARN: Removed duplicated region for block: B:100:0x0107 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:74:0x0151 A[Catch: all -> 0x015e, TRY_LEAVE, TryCatch #15 {all -> 0x015e, blocks: (B:58:0x00e6, B:60:0x00ea, B:62:0x00f6, B:96:0x010d, B:98:0x0111, B:84:0x012d, B:86:0x0131, B:72:0x014d, B:74:0x0151), top: B:11:0x002b }] */
    /* JADX WARN: Removed duplicated region for block: B:76:0x0147 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:86:0x0131 A[Catch: all -> 0x015e, TRY_LEAVE, TryCatch #15 {all -> 0x015e, blocks: (B:58:0x00e6, B:60:0x00ea, B:62:0x00f6, B:96:0x010d, B:98:0x0111, B:84:0x012d, B:86:0x0131, B:72:0x014d, B:74:0x0151), top: B:11:0x002b }] */
    /* JADX WARN: Removed duplicated region for block: B:88:0x0127 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:98:0x0111 A[Catch: all -> 0x015e, TRY_LEAVE, TryCatch #15 {all -> 0x015e, blocks: (B:58:0x00e6, B:60:0x00ea, B:62:0x00f6, B:96:0x010d, B:98:0x0111, B:84:0x012d, B:86:0x0131, B:72:0x014d, B:74:0x0151), top: B:11:0x002b }] */
    /* renamed from: а, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static com.taobao.wireless.security.adapter.common.C0008 m32(java.lang.String r8, java.util.Map<java.lang.String, java.lang.String> r9) {
        /*
            Method dump skipped, instructions count: 382
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.common.C0009.m32(java.lang.String, java.util.Map):com.taobao.wireless.security.adapter.common.б");
    }

    /* JADX WARN: Code restructure failed: missing block: B:140:0x011a, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L73;
     */
    /* JADX WARN: Code restructure failed: missing block: B:36:0x01a0, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L73;
     */
    /* JADX WARN: Code restructure failed: missing block: B:37:0x011c, code lost:
    
        android.net.TrafficStats.clearThreadStatsTag();
     */
    /* JADX WARN: Code restructure failed: missing block: B:57:0x0179, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L73;
     */
    /* JADX WARN: Code restructure failed: missing block: B:75:0x0152, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L73;
     */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Not initialized variable reg: 2, insn: 0x01ad: IF  (r2 I:??[int, boolean, OBJECT, ARRAY, byte, short, char]) == (0 ??[int, boolean, OBJECT, ARRAY, byte, short, char])  -> B:163:0x01b2, block:B:162:0x01ad */
    /* JADX WARN: Removed duplicated region for block: B:35:0x0196 A[Catch: all -> 0x01a4, TRY_LEAVE, TryCatch #2 {all -> 0x01a4, blocks: (B:137:0x010c, B:139:0x0110, B:37:0x011c, B:72:0x0144, B:74:0x0148, B:54:0x016b, B:56:0x016f, B:33:0x0192, B:35:0x0196), top: B:10:0x0025 }] */
    /* JADX WARN: Removed duplicated region for block: B:40:0x018c A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:44:0x0185 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:56:0x016f A[Catch: all -> 0x01a4, TRY_LEAVE, TryCatch #2 {all -> 0x01a4, blocks: (B:137:0x010c, B:139:0x0110, B:37:0x011c, B:72:0x0144, B:74:0x0148, B:54:0x016b, B:56:0x016f, B:33:0x0192, B:35:0x0196), top: B:10:0x0025 }] */
    /* JADX WARN: Removed duplicated region for block: B:58:0x0165 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:62:0x015e A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:74:0x0148 A[Catch: all -> 0x01a4, TRY_LEAVE, TryCatch #2 {all -> 0x01a4, blocks: (B:137:0x010c, B:139:0x0110, B:37:0x011c, B:72:0x0144, B:74:0x0148, B:54:0x016b, B:56:0x016f, B:33:0x0192, B:35:0x0196), top: B:10:0x0025 }] */
    /* JADX WARN: Removed duplicated region for block: B:76:0x013e A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:80:0x0137 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* renamed from: а, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static com.taobao.wireless.security.adapter.common.C0008 m33(java.lang.String r5, java.util.Map<java.lang.String, java.lang.String> r6, byte[] r7, java.lang.String r8, int r9, int r10) {
        /*
            Method dump skipped, instructions count: 465
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.common.C0009.m33(java.lang.String, java.util.Map, byte[], java.lang.String, int, int):com.taobao.wireless.security.adapter.common.б");
    }

    /* JADX WARN: Code restructure failed: missing block: B:104:0x0115, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L76;
     */
    /* JADX WARN: Code restructure failed: missing block: B:120:0x00f0, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L76;
     */
    /* JADX WARN: Code restructure failed: missing block: B:86:0x0137, code lost:
    
        if (com.taobao.wireless.security.adapter.common.C0009.f27.getPackageName().equalsIgnoreCase("com.alibaba.android.rimet") != false) goto L76;
     */
    /* JADX WARN: Code restructure failed: missing block: B:87:0x00f2, code lost:
    
        android.net.TrafficStats.clearThreadStatsTag();
     */
    /* renamed from: а, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String m34(java.lang.String r8, java.lang.String r9) {
        /*
            Method dump skipped, instructions count: 315
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.common.C0009.m34(java.lang.String, java.lang.String):java.lang.String");
    }

    /* renamed from: а, reason: contains not printable characters */
    private static URLConnection m35(String str, int i, int i2, String str2) {
        URLConnection uRLConnection = null;
        if (!str.startsWith("http")) {
            return null;
        }
        if (Build.VERSION.SDK_INT < 8) {
            System.setProperty("http.keepAlive", "false");
        }
        uRLConnection = new URL(str).openConnection();
        if (i <= 0) {
            i = HttpClientUtils.TIMEOUT_DEFAULT;
        }
        if (i2 <= 0) {
            i2 = HttpClientUtils.TIMEOUT_DEFAULT;
        }
        if (str.startsWith("https")) {
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) uRLConnection;
            httpsURLConnection.setConnectTimeout(i);
            httpsURLConnection.setReadTimeout(i2);
            httpsURLConnection.setRequestMethod(str2);
        } else {
            HttpURLConnection httpURLConnection = (HttpURLConnection) uRLConnection;
            httpURLConnection.setConnectTimeout(i);
            httpURLConnection.setReadTimeout(i2);
            httpURLConnection.setRequestMethod(str2);
        }
        uRLConnection.setUseCaches(false);
        if ("POST".equals(str2)) {
            uRLConnection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            uRLConnection.setDoOutput(true);
        }
        uRLConnection.setRequestProperty("Accept-Charset", "UTF-8");
        return uRLConnection;
    }

    /* renamed from: а, reason: contains not printable characters */
    public static void m36(Context context) {
        if (context != null) {
            f27 = context;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    private static void m37(URLConnection uRLConnection) {
        if (uRLConnection != null) {
            try {
                if (uRLConnection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) uRLConnection).disconnect();
                } else if (uRLConnection instanceof HttpURLConnection) {
                    ((HttpURLConnection) uRLConnection).disconnect();
                }
            } catch (Throwable unused) {
            }
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    private static int m38(URLConnection uRLConnection) throws IOException {
        return uRLConnection instanceof HttpsURLConnection ? ((HttpsURLConnection) uRLConnection).getResponseCode() : ((HttpURLConnection) uRLConnection).getResponseCode();
    }
}
