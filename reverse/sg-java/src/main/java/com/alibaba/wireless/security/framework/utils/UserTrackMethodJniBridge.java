package com.alibaba.wireless.security.framework.utils;

/**
 * 原类是接入阿里 UT（UserTrack）的上报桥，反编译出来带着一堆 app 侧 SDK 依赖。
 * 我们只要 native 能通过 GetStaticMethodID 找到它 —— 行为无所谓，统一关掉上报。
 */
public class UserTrackMethodJniBridge {

    /** native 初始化时会查这个方法：UT 是否可用。0 = 不可用（不上报）。 */
    public static int utAvaiable() {
        // 必须返回 1：middletier 的 70101 会先问 UT 是否可用，返回 0 就直接抛 SecException(2405)
        return 1;
    }

    public static int commitUT(String eventId, String label, String page, String arg1, String arg2,
                               String arg3, long arg4, long arg5) {
        return 0;
    }

    public static int commitUT(String eventId, String label, String page) {
        return 0;
    }

    public static int addUtRecord(String a, int b, int c, String d, long e,
                                  String f, String g, String h, String i, String j) {
        return 0;
    }
}
