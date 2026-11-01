package com.tmall;

import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;

/** 快速入口：只做插件初始化 + 一次 wua，用于验证 hook / 后端等基础设施。 */
public class QuickRun {
    public static void main(String[] args) throws Exception {
        SGRunner runner = new SGRunner(Boolean.getBoolean("sg.verbose"));
        ISecurityBodyComponent body = runner.initPlugins();
        if (body == null) {
            System.out.println("[Q] initPlugins failed");
            Runtime.getRuntime().halt(1);
        }
        long sleepMs = Long.getLong("sg.sleep", 0L);
        if (sleepMs > 0) {
            System.out.println("[Q] 等待 SG 异步初始化 " + sleepMs + "ms");
            Thread.sleep(sleepMs);
        }
        String ts = String.valueOf(System.currentTimeMillis());
        java.util.HashMap<String, String> ext = new java.util.HashMap<>();
        ext.put("api", "mtop.common.gettimestamp");
        ext.put("v", "1.0");
        ext.put("data", "{}");

        // 1) 我们的写法：ts/appKey/authCode 都非空
        probe(body, "ts+appKey+empty", ts, "12574478", "", null);
        // 2) middletier getMiniWua 的真实写法：三个都传 null
        probe(body, "all-null", null, null, null, null);
        // 3) null + extend params
        probe(body, "null+ext", null, null, null, ext);
        // 4) ts/appKey + ext
        probe(body, "ts+appKey+ext", ts, "12574478", "", ext);
        // 模块 6 = AVMP。看这些命令有没有 handler（9904 = 没注册）
        int[] cmds = {60101, 70201, 10201, 10301, 10501, 10601, 20102, 30101, 50101};
        for (int c : cmds) {
            try {
                Object r = runner.probe(c, "mwua", "sgcipher2");
                System.out.println("[MOD] " + c + " -> " + SGHarness.describe(r)
                        + "  err=" + com.alibaba.wireless.security.open.SecException.lastErrorCode);
            } catch (Throwable t) {
                System.out.println("[MOD] " + c + " 异常: " + t);
            }
            com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
        }

        // 直接打 10401，遍历 requestType，看哪类能出签名
        java.util.HashMap<Integer, String> rtErr = new java.util.LinkedHashMap<>();
        for (int rt = 1; rt <= 10; rt++) {
            try {
                java.util.HashMap<String, String> m = new java.util.HashMap<>();
                m.put("INPUT", "test_input_for_sign");
                Object r = runner.probe(10401, m, SGRunner.APP_KEY, rt, "", Boolean.TRUE);
                String desc = SGHarness.describe(r);
                System.out.println("[RT] " + rt + " -> " + desc);
                rtErr.put(rt, desc);
            } catch (Throwable t) {
                System.out.println("[RT] " + rt + " 异常: " + t);
            }
            com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
        }

        Runtime.getRuntime().halt(0);
    }

    private static void probe(ISecurityBodyComponent body, String tag, String ts, String appKey,
                              String authCode, java.util.HashMap<String, String> ext) {
        try {
            String w = body.getSecurityBodyDataEx(ts, appKey, authCode, ext, 8, 0);
            System.out.println("[Q] " + tag + " -> "
                    + (w == null ? "null" : w.length() + " chars: " + w));
        } catch (Throwable t) {
            System.out.println("[Q] " + tag + " 异常: " + t);
        }
        com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
    }
}
