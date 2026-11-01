package com.tmall;

import java.util.HashMap;

/**
 * wua 长度扫描：20102 的 flag/env/参数形态哪个组合能出"长 wua"（>100 字符）。
 * 已知真机是 197 字符，我们一直只拿到 69（51 字节）。
 */
public class WuaSweep {

    public static void main(String[] args) throws Exception {
        System.setProperty("sg.apkmap", "true");
        SGRunner runner = new SGRunner(false);
        runner.initPlugins();

        String ts = String.valueOf(System.currentTimeMillis());
        String appKey = SGRunner.APP_KEY;
        // 注意：20102 第 5 个参数是 String（Java 层先把它 HashMap URLEncode 拼成 a=b&c=d）
        String ext = "api=mtop.taobao.mloginService.smsSend&v=1.0";

        int[] flags = {1, 2, 4, 8, 16, 32, 3, 5, 6, 7, 9, 10, 12, 15};
        for (int env = 0; env <= 2; env++) {
            for (int flag : flags) {
                com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
                Object r = runner.probeQuiet(20102, ts, appKey, Integer.valueOf(flag), "", ext, Integer.valueOf(env));
                int err = com.alibaba.wireless.security.open.SecException.lastErrorCode;
                String s = r == null ? null : String.valueOf(r);
                System.out.println("[W] env=" + env + " flag=" + flag + " -> "
                        + (s == null ? "null" : s.length() + "ch") + " err=" + err
                        + (s != null && s.length() > 90 ? "  <<< LONG" : ""));
            }
        }
        Runtime.getRuntime().halt(0);
    }
}
