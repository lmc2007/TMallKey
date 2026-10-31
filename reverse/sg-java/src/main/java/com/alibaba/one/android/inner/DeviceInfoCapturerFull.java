package com.alibaba.one.android.inner;

import android.content.Context;

import java.util.Locale;

/**
 * 原类负责采集「全量」设备指纹（证书链、音频路由、传感器…），jadx 反编译出来是残骸
 * （缺 return、变量未初始化），而且我们只走 wua 生成，用不到这些字段。
 *
 * 这里保留同名 API，返回稳定但无意义的空值 —— 需要真实设备指纹时再逐项实现。
 */
public class DeviceInfoCapturerFull {

    public static Object c = new Object();

    /** 内部承接入参 Context 的字段（jadx 里叫 f5a）。 */
    public static Context f5a;

    public static void initialize(Context context) {
        f5a = context;
    }

    public static String a() {
        return "";
    }

    public static String b() {
        return "";
    }

    public static String c() {
        return "";
    }

    public static String d() {
        return "";
    }

    public static String e() {
        return "";
    }

    public static Locale f() {
        return Locale.CHINA;
    }

    public static int audioRoute() {
        return 0;
    }

    public static String getAudioVolume() {
        return "";
    }

    public static String getUid(String str) {
        return "";
    }

    public static String doCommandForString(int code) {
        return "";
    }
}
