package com.taobao.wireless.security.adapter.common;

import java.util.Map;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class HttpUtil {

    /* renamed from: а, reason: contains not printable characters */
    private static int f21 = 1000;

    public static String downloadFileBridge(String str, String str2) {
        return C0009.m34(str, str2);
    }

    public static String sendSyncHttpGetRequestBridge(String str) {
        C0008 m32 = C0009.m32(str, (Map<String, String>) null);
        if (m32 != null) {
            return m32.f24;
        }
        return null;
    }

    /* JADX WARN: Removed duplicated region for block: B:21:0x0060 A[Catch: Exception -> 0x0063, TRY_LEAVE, TryCatch #0 {Exception -> 0x0063, blocks: (B:19:0x004a, B:21:0x0060), top: B:18:0x004a }] */
    /* JADX WARN: Removed duplicated region for block: B:26:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String sendSyncHttpPostRequestBridge(java.lang.String r7, java.lang.String r8, java.lang.String r9, int r10, int r11) {
        /*
            r0 = 0
            if (r7 == 0) goto L63
            int r1 = r7.length()
            if (r1 == 0) goto L63
            if (r9 == 0) goto L63
            int r1 = r9.length()
            if (r1 != 0) goto L12
            goto L63
        L12:
            r1 = 1
            if (r10 != r1) goto L35
            if (r8 == 0) goto L34
            int r10 = r8.length()
            if (r10 != 0) goto L1e
            goto L34
        L1e:
            java.lang.String[] r10 = new java.lang.String[r1]
            r1 = 0
            r10[r1] = r8
            boolean r10 = com.taobao.wireless.security.adapter.common.C0007.m30(r10)
            if (r10 == 0) goto L45
            java.util.HashMap r10 = new java.util.HashMap
            r10.<init>()
            java.lang.String r1 = "keyindex"
            r10.put(r1, r8)
            goto L45
        L34:
            return r0
        L35:
            r8 = 2
            if (r10 != r8) goto L45
            java.lang.String r8 = "+"
            java.lang.String r10 = "%2B"
            java.lang.String r8 = r9.replace(r8, r10)
            byte[] r8 = r8.getBytes()
            goto L49
        L45:
            byte[] r8 = r9.getBytes()
        L49:
            r3 = r8
            java.lang.String r4 = "application/x-www-form-urlencoded;charset=UTF-8"
            r2 = 0
            int r8 = com.taobao.wireless.security.adapter.common.HttpUtil.f21     // Catch: java.lang.Exception -> L63
            int r5 = r11 * r8
            int r8 = com.taobao.wireless.security.adapter.common.HttpUtil.f21     // Catch: java.lang.Exception -> L63
            int r6 = r11 * r8
            r1 = r7
            com.taobao.wireless.security.adapter.common.б r7 = com.taobao.wireless.security.adapter.common.C0009.m33(r1, r2, r3, r4, r5, r6)     // Catch: java.lang.Exception -> L63
            int r8 = r7.f25     // Catch: java.lang.Exception -> L63
            r9 = 200(0xc8, float:2.8E-43)
            if (r8 != r9) goto L63
            java.lang.String r7 = r7.f24     // Catch: java.lang.Exception -> L63
            r0 = r7
        L63:
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.common.HttpUtil.sendSyncHttpPostRequestBridge(java.lang.String, java.lang.String, java.lang.String, int, int):java.lang.String");
    }
}
