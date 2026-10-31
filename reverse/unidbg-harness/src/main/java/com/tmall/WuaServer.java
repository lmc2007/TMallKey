package com.tmall;

import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * wua 常驻服务（阶段 5 的集成形态）。
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.tmall.WuaServer -Dexec.args="17321"
 *   curl "http://127.0.0.1:17321/wua?appKey=12574478&amp;flag=8"
 * </pre>
 *
 * 与 {@link WuaCli} 的区别：进程常驻，SG 只初始化一次（~1.2s），之后每次只需 ~210ms；
 * 免得每次调用都重走一遍 JVM + unidbg 启动。native 不是线程安全的，所以处理线程固定为 1。
 *
 * 路由：
 * <pre>
 *   GET /healthz                               -> ok
 *   GET /wua?appKey=&amp;flag=&amp;ts=&amp;authCode=   -> {"ok":true,"wua":"...","len":69,"genMs":210}
 * </pre>
 *
 * 绑定 127.0.0.1：这是本机辅助进程，不对外暴露。
 */
public class WuaServer {

    private static SGRunner runner;
    private static ISecurityBodyComponent body;
    private static long readyAt;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 17321;

        System.out.println("[SRV] 初始化 SG（约 1~2s）...");
        long t0 = System.currentTimeMillis();
        runner = new SGRunner(false);
        body = runner.initPlugins();
        if (body == null) {
            System.out.println("[SRV] 初始化失败，退出");
            Runtime.getRuntime().halt(1);
        }
        readyAt = System.currentTimeMillis() - t0;

        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        server.createContext("/healthz", ex -> respond(ex, 200,
                "{\"ok\":true,\"readyMs\":" + readyAt + "}"));
        server.createContext("/wua", WuaServer::handleWua);
        server.setExecutor(Executors.newFixedThreadPool(1));
        server.start();
        System.out.println("[SRV] listening on http://127.0.0.1:" + port
                + "  (init " + readyAt + "ms)");
    }

    private static void handleWua(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        String appKey = q.getOrDefault("appKey", "12574478");
        int flag;
        try {
            flag = Integer.parseInt(q.getOrDefault("flag", "8"));
        } catch (NumberFormatException e) {
            flag = 8;
        }
        String ts = q.getOrDefault("ts", String.valueOf(System.currentTimeMillis()));
        String authCode = q.getOrDefault("authCode", "");

        long t0 = System.currentTimeMillis();
        try {
            String wua = body.getSecurityBodyDataEx(ts, appKey, authCode, null, flag, 0);
            long ms = System.currentTimeMillis() - t0;
            if (wua == null) {
                respond(ex, 502, "{\"ok\":false,\"error\":\"wua null\",\"genMs\":" + ms + "}");
                return;
            }
            respond(ex, 200, "{\"ok\":true,\"wua\":\"" + wua + "\",\"len\":" + wua.length()
                    + ",\"ts\":\"" + ts + "\",\"flag\":" + flag + ",\"genMs\":" + ms + "}");
        } catch (Throwable t) {
            long ms = System.currentTimeMillis() - t0;
            respond(ex, 500, "{\"ok\":false,\"error\":\"" + esc(String.valueOf(t))
                    + "\",\"genMs\":" + ms + "}");
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
