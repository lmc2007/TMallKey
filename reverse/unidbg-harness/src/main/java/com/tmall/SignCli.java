package com.tmall;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.HashMap;

/**
 * x-sign / x-mini-wua 生成器（命令行）。
 *
 * INPUT 串严格照 mtopsdk 的 InnerSignImpl.convertInnerBaseStrMap() 拼 22 段：
 *   utdid & uid & reqbiz-ext & appKey & md5(data) & t & api & v & sid & ttid
 *   & deviceId & lat & lng & extdata & x-features & routerId & placeId
 *   & open-biz & mini-appkey & req-appkey & access-token & open-biz-data
 *
 * 用法（必须带 -Dsg.apkmap=true，否则 SG 读不到 APK 会抛 608）：
 *   mvn -s settings.xml -q exec:java -Dexec.mainClass=com.tmall.SignCli -Dsg.apkmap=true \
 *     -Dexec.args="--api=mtop.taobao.mloginService.smsSend --v=1.0 --data={}"
 */
public class SignCli {

    private static final HashMap<String, String> A = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.setProperty("sg.apkmap", "true");
        for (String a : args) {
            if (a.startsWith("--") && a.contains("=")) {
                int i = a.indexOf('=');
                A.put(a.substring(2, i), a.substring(i + 1));
            }
        }
        String api = get("api", "mtop.common.gettimestamp");
        String v = get("v", "1.0");
        String data = get("data", "{}");
        String dataFile = get("datafile", null);
        if (dataFile != null) {
            data = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(dataFile)),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        String t = get("t", String.valueOf(System.currentTimeMillis()));
        String utdid = get("utdid", "");
        String uid = get("uid", "");
        String sid = get("sid", "");
        String ttid = get("ttid", "600000@taobao_android_5.0.0");
        String devid = get("devid", "");
        String xfeatures = get("xfeatures", "27");

        StringBuilder sb = new StringBuilder(128);
        sb.append(utdid).append('&')
          .append(uid).append('&')
          .append(get("reqbiz", "")).append('&')
          .append(SGRunner.APP_KEY).append('&')
          .append(md5(data)).append('&')
          .append(t).append('&')
          .append(api).append('&')
          .append(v).append('&')
          .append(sid).append('&')
          .append(ttid).append('&')
          .append(devid).append('&')
          .append(get("lat", "")).append('&')
          .append(get("lng", "")).append('&')
          .append(get("extdata", "")).append('&')
          .append(xfeatures).append('&')
          .append(get("routerid", "")).append('&')
          .append(get("placeid", "")).append('&')
          .append(get("openbiz", "")).append('&')
          .append(get("miniappkey", "")).append('&')
          .append(get("reqappkey", "")).append('&')
          .append(get("accesstoken", "")).append('&')
          .append(get("openbizdata", ""));
        String input = sb.toString();

        SGRunner runner = new SGRunner(false);
        com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent body = runner.initPlugins();

        HashMap<String, String> m = new HashMap<>();
        m.put("INPUT", input);
        Object sign = runner.probeQuiet(10401, m, SGRunner.APP_KEY, 7, "", Boolean.TRUE);
        int err = com.alibaba.wireless.security.open.SecException.lastErrorCode;
        String xs = sign == null ? null : String.valueOf(sign);

        // wua：flag 决定长短。8 = 短(69)，1/2/4/16 = 长(261)，32 = 157
        int wuaFlag = Integer.parseInt(get("wuaflag", "4"));
        String wua = null;
        try {
            String extStr = "api=" + api + "&v=" + v;
            Object w = runner.probeQuiet(20102, t, SGRunner.APP_KEY, Integer.valueOf(wuaFlag),
                    "", extStr, Integer.valueOf(0));
            wua = w == null ? null : String.valueOf(w);
        } catch (Throwable t2) {
            System.out.println("[WARN] wua: " + t2);
        }

        System.out.println("__SIGN_JSON__{\"x-sign\":\"" + nz(xs) + "\",\"x-mini-wua\":\"" + nz(wua)
                + "\",\"t\":\"" + t + "\",\"err\":" + err + ",\"input\":\"" + esc(input) + "\"}");
        System.out.println("[SIGN] err=" + err + " x-sign=" + xs);
        Runtime.getRuntime().halt(0);
    }

    private static String get(String k, String def) {
        String v = A.get(k);
        return v == null ? def : v;
    }

    private static String nz(String s) {
        return s == null ? "" : esc(s);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
