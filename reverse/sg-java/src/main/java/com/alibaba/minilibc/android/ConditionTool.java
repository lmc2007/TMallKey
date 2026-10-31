package com.alibaba.minilibc.android;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class ConditionTool {
    public static String convertEmptyStringToNull(String str) {
        if (str == null || str.length() != 0) {
            return str;
        }
        return null;
    }

    public static boolean stringEquals(String str, String str2) {
        if (str == null && str2 == null) {
            return true;
        }
        return (str == null || str2 == null || !str.equals(str2)) ? false : true;
    }

    public static boolean stringNotNullOrEmpty(String... strArr) {
        return !stringNullOrEmpty(strArr);
    }

    public static boolean stringNullOrEmpty(String... strArr) {
        for (String str : strArr) {
            if (str == null || "".equals(str)) {
                return true;
            }
        }
        return false;
    }
}
