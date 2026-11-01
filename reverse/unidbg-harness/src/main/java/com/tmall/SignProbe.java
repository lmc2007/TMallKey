package com.tmall;

import java.util.HashMap;

/**
 * 只测 10401(签名)：验证"缺 ATLAS 等 map 键"是否就是 608 的成因。
 * 线索来自 middletier 的 sign_v2 调用：rt=2 时会往 map 里塞 {"INPUT", "ATLAS":"daily"}。
 */
public class SignProbe {

    public static void main(String[] args) throws Exception {
        SGRunner runner = new SGRunner(false);
        runner.initPlugins();

        String input = "test_input_for_sign";

        // 组合：不同 map 内容 × requestType
        String[][] combos = new String[][]{
                {},                        // 只有 INPUT
                {"ATLAS", "daily"},
                {"ATLAS", "1"},
                {"ATLAS", "0"},
                {"SEEDKEY", ""},
                {"KEY", ""},
                {"AES_KEY", ""},
        };
        int[] types = new int[]{2, 7, 3};

        for (int rt : types) {
            for (String[] extra : combos) {
                HashMap<String, String> m = new HashMap<>();
                m.put("INPUT", input);
                StringBuilder tag = new StringBuilder("INPUT");
                for (int i = 0; i + 1 < extra.length; i += 2) {
                    m.put(extra[i], extra[i + 1]);
                    tag.append("+").append(extra[i]).append("=").append(extra[i + 1]);
                }
                com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
                long t0 = System.currentTimeMillis();
                com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
                Object r;
                try {
                    r = runner.probeQuiet(10401, m, SGRunner.APP_KEY, rt, "", Boolean.TRUE);
                } catch (Throwable t) {
                    System.out.println("[S] rt=" + rt + " " + tag + " 异常 " + t);
                    continue;
                }
                System.out.println("[S] rt=" + rt + " " + tag + " -> " + SGHarness.describe(r)
                        + "  err=" + com.alibaba.wireless.security.open.SecException.lastErrorCode
                        + "  " + (System.currentTimeMillis() - t0) + "ms");
            }
        }
        Runtime.getRuntime().halt(0);
    }
}
