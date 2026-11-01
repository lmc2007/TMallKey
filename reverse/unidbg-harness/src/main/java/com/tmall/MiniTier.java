package com.tmall;

import java.util.LinkedHashMap;
import java.util.Map;

/** 只打一发 middletier：70101(init) → 70102(因子)。避免连打两次触发 unicorn 写保护崩溃。 */
public class MiniTier {

    public static void main(String[] args) throws Exception {
        System.setProperty("sg.apkmap", "true");
        SGRunner runner = new SGRunner(Boolean.getBoolean("sg.verbose"));
        runner.initPlugins();
        // middletier 的 onPluginLoaded 里 Executors 起了一个异步初始化线程；不等它跑完就打 70101 会拿到 2405
        long wait = Long.getLong("sg.sleep", 5000L);
        if (wait > 0) {
            System.out.println("[M] 等 middletier 异步初始化 " + wait + "ms");
            Thread.sleep(wait);
        }

        String appKey = SGRunner.APP_KEY;
        String utdid = System.getProperty("sg.utdid", "apU98phzUu4DAOxRSTtO69oT");
        String devId = System.getProperty("sg.deviceid",
                "Antrb7uDc9emwB3hbOSi9Sr9tbLbhkIE5qPr-PwUlXva");
        String api = args.length > 0 ? args[0] : "mtop.common.gettimestamp";
        String t = String.valueOf(System.currentTimeMillis());
        String data = utdid + "&&&" + appKey + "&" + SGRunner.md5Hex("{}") + "&" + t
                + "&" + api + "&1.0&&600000@taobao_android_5.0.0&" + devId + "&&&&27&&&&&&&";

        com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
        Object init = runner.probeQuiet(70101, "", Integer.valueOf(0));
        System.out.println("[M] 70101 -> " + SGHarness.describe(init)
                + " err=" + com.alibaba.wireless.security.open.SecException.lastErrorCode);

        String requestId = "req-" + t;
        com.alibaba.wireless.security.open.SecException.lastErrorCode = 0;
        Object r = runner.probeQuiet(70102, appKey, data, Boolean.FALSE, Integer.valueOf(0),
                api, "pageId=&pageName=", "", null, null, requestId,
                Integer.valueOf(0), Integer.valueOf(0));
        System.out.println("[M] 70102 -> " + SGHarness.describe(r)
                + " err=" + com.alibaba.wireless.security.open.SecException.lastErrorCode);
        if (r instanceof Map) {
            Map<?, ?> snap = new LinkedHashMap<>((Map<?, ?>) r);
            for (Map.Entry<?, ?> e : snap.entrySet()) {
                String v = String.valueOf(e.getValue());
                System.out.println("[M]    " + e.getKey() + " (" + v.length() + ") = "
                        + (v.length() > 80 ? v.substring(0, 80) + "..." : v));
            }
        }
        Runtime.getRuntime().halt(0);
    }
}
