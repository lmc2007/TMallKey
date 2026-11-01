package com.tmall;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 只测 middletier 的多因子签名（70102 getSecurityFactors）——这是 x-sgext 的来源。
 * 目的：确认 10401(x-sign) 卡死时，能不能先拿到 x-sgext。
 */
public class UnifiedProbe {

    public static void main(String[] args) throws Exception {
        SGRunner runner = new SGRunner(false);
        runner.initPlugins();

        String api = args.length > 0 ? args[0] : "mtop.common.gettimestamp";
        String utdid = System.getProperty("sg.utdid", "apU98phzUu4DAOxRSTtO69oT");
        String devId = System.getProperty("sg.deviceid",
                "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva");
        String time = String.valueOf(System.currentTimeMillis());
        String md5 = SGRunner.md5Hex("{}");
        String data = utdid + "&&&12574478&" + md5 + "&" + time + "&" + api
                + "&1.0&&600000@taobao_android_5.0.0&" + devId + "&&&&27&&&&&&&";

        String requestId = "req-" + System.currentTimeMillis();
        // 先打 middletier 的 init（70101），它失败的话 70102 一定拿不到东西
        for (String ac : new String[]{"", "h5"}) {
            com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
            Object init = runner.probeQuiet(70101, ac, Integer.valueOf(0));
            System.out.println("[U] 70101 auth='" + ac + "' -> " + SGHarness.describe(init)
                    + " err=" + com.alibaba.wireless.security.open.SecException.lastErrorCode);
        }
        for (boolean uw : new boolean[]{false, true}) {
            for (String auth : new String[]{null, ""}) {
                com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
                long t0 = System.currentTimeMillis();
                // 严格照 middletier 的 Java 包装（d/d/a.a）传 12 个参数，少一个 native 就可能读飞
                Object r = runner.probeQuiet(70102, SGRunner.APP_KEY, data, Boolean.valueOf(uw), 0,
                        api, "pageId=&pageName=", auth, null, null, requestId,
                        Integer.valueOf(0), Integer.valueOf(0));
                int err = com.alibaba.wireless.security.open.SecException.lastErrorCode;
                System.out.println("[U] useWua=" + uw + " auth=" + (auth == null ? "null" : "\"\"")
                        + " err=" + err + " " + (System.currentTimeMillis() - t0) + "ms");
                if (r instanceof Map) {
                    Map<?, ?> snap = new LinkedHashMap<>((Map<?, ?>) r);
                    System.out.println("[U]   keys=" + snap.keySet());
                    for (Map.Entry<?, ?> en : snap.entrySet()) {
                        String v = String.valueOf(en.getValue());
                        System.out.println("[U]   " + en.getKey() + " (" + v.length() + ") = "
                                + (v.length() > 90 ? v.substring(0, 90) + "..." : v));
                    }
                } else {
                    System.out.println("[U]   ret=" + SGHarness.describe(r));
                }
            }
        }
        Runtime.getRuntime().halt(0);
    }
}
