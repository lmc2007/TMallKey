package com.alibaba.one.sdk;

import java.security.MessageDigest;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class h {

    /* renamed from: a, reason: collision with root package name */
    public static h f14a;

    public static String a(String str) {
        if (str != null) {
            try {
                if (str.length() != 0) {
                    MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
                    messageDigest.update(str.getBytes("UTF-8"));
                    byte[] digest = messageDigest.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) {
                        sb.append(String.format("%02x", Byte.valueOf(b)));
                    }
                    return sb.toString();
                }
            } catch (Exception unused) {
            }
        }
        return null;
    }
}
