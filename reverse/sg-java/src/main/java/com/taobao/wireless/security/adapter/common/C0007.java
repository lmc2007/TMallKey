package com.taobao.wireless.security.adapter.common;

/* renamed from: com.taobao.wireless.security.adapter.common.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0007 {
    /* renamed from: а, reason: contains not printable characters */
    public static boolean m30(String... strArr) {
        return !m31(strArr);
    }

    /* renamed from: б, reason: contains not printable characters */
    public static boolean m31(String... strArr) {
        for (String str : strArr) {
            if (str == null || "".equals(str)) {
                return true;
            }
        }
        return false;
    }
}
