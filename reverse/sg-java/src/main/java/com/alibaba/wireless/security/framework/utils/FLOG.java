package com.alibaba.wireless.security.framework.utils;

import android.os.Process;
import android.util.Log;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public class FLOG {
    public static String a = "SGTIME";
    public static boolean b = false;

    public static void d(String str) {
    }

    public static void e(String str) {
    }

    public static String get_currentFuncName() {
        if (b) {
            return Thread.currentThread().getStackTrace()[3].getMethodName();
        }
        return null;
    }

    public static long get_currentTime() {
        if (b) {
            return System.currentTimeMillis();
        }
        return 0L;
    }

    public static void i(String str) {
    }

    public static void printTimeCost(String str, String str2, String str3, long j) {
        if (b) {
            Log.e(a, "[" + Process.myPid() + "][" + Process.myTid() + "][" + str + "][JAVA][" + str2 + "] cost = " + (System.currentTimeMillis() - j) + " ms [" + str3 + "]");
        }
    }

    public static void w(String str) {
    }

    public static void w(String str, Throwable th) {
    }
}
