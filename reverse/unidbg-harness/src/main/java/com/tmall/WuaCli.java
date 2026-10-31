package com.tmall;

import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;

/**
 * wua 生成器命令行（阶段 5 集成的最小形态）。
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.tmall.WuaCli -Dexec.args="12574478 8"
 * </pre>
 *
 * stdout 里其它行是初始化日志，结果固定在 {@code __WUA_JSON__ } 前缀那一行，
 * 调用方 grep 该前缀即可（这样不必为了 CLI 去改 SGRunner 的日志）。
 *
 * 参数：{@code appKey [flag] [timestamp] [authCode]}
 */
public class WuaCli {

    public static void main(String[] args) {
        String appKey = args.length > 0 ? args[0] : "12574478";
        int flag = args.length > 1 ? Integer.parseInt(args[1]) : 8;
        String ts = args.length > 2 ? args[2] : String.valueOf(System.currentTimeMillis());
        String authCode = args.length > 3 ? args[3] : "";

        long t0 = System.currentTimeMillis();
        SGRunner runner = null;
        try {
            runner = new SGRunner(false);
            ISecurityBodyComponent body = runner.initPlugins();
            if (body == null) {
                emit("{\"ok\":false,\"error\":\"plugin init failed\",\"appKey\":\"" + appKey + "\"}");
                halt(1);
            }
            long initMs = System.currentTimeMillis() - t0;
            long t1 = System.currentTimeMillis();
            String wua = body.getSecurityBodyDataEx(ts, appKey, authCode, null, flag, 0);
            long genMs = System.currentTimeMillis() - t1;

            // 只报长度和内容，不报长度之外的结构（避免脚本侧猜）
            emit("{\"ok\":" + (wua != null) + ",\"appKey\":\"" + appKey + "\",\"flag\":" + flag
                    + ",\"ts\":\"" + ts + "\",\"wua\":" + (wua == null ? "null" : "\"" + esc(wua) + "\"")
                    + ",\"len\":" + (wua == null ? 0 : wua.length())
                    + ",\"initMs\":" + initMs + ",\"genMs\":" + genMs + "}");
            halt(wua == null ? 2 : 0);
        } catch (Throwable t) {
            emit("{\"ok\":false,\"error\":\"" + esc(String.valueOf(t)) + "\"}");
            halt(3);
        } finally {
            if (runner != null) {
                try {
                    runner.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void emit(String json) {
        System.out.println("__WUA_JSON__ " + json);
        System.out.flush();
    }

    /** unicorn 在 JVM 正常退出时会 AV 崩溃，用 halt 跳过 shutdown 流程。 */
    private static void halt(int code) {
        System.out.flush();
        Runtime.getRuntime().halt(code);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
