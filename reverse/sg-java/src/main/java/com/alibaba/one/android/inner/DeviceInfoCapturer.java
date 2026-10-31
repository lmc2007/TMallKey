package com.alibaba.one.android.inner;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原类是「one SDK」的设备指纹采集器（13 个 obfuscated 类互相引用），jadx 反编译出来是残骸。
 *
 * 我们只需要它存在且 native 调 doCommandForString(int) 不炸 —— 设备指纹先全部返回空串，
 * 等 wua 能出串、服务端接受性验证通过后，再按需要填真值（真机抓包 / 模板灌入）。
 */
public class DeviceInfoCapturer {

    public static Context f4a;
    public static TelephonyManager b;
    public static volatile String c;
    public static volatile String d;
    public static Map<Integer, String> e = new HashMap<>();
    public static Map<Integer, Integer> f = new HashMap<>();
    public static final List<Integer> g = Arrays.asList(0, 1, 18, 11, 15, 12);

    public static void initialize(Context context) {
        f4a = context;
    }

    public static String a(String key) {
        return "";
    }

    public static String a(boolean z) {
        return "";
    }

    public static boolean a() {
        return false;
    }

    public static String b() {
        return "";
    }

    public static String b(String str) {
        return "";
    }

    public static String c() {
        return "";
    }

    public static boolean c(String str) {
        return str != null && str.length() > 0;
    }

    public static String doCommandForString(int code) {
        System.out.println("[PROBE] one.doCommandForString(" + code + ")");
        return "";
    }
}
