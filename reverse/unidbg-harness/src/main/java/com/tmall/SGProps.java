package com.tmall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 真机 Android property 表。
 *
 * <p>SG 的 libmain 会直接调 libc 的 __system_property_get 读设备属性（不是走 Java 的
 * android.os.Build），unidbg 默认没有 provider，读到的全是空串 —— 这会让 SG 拿不到
 * 机型/指纹，产出的 x-mini-wua 只有 69 字符（真机是 197）。
 *
 * <p>props.txt 由 {@code adb shell getprop} 导出，格式 {@code key=value}。
 */
public final class SGProps {

    private static final Map<String, String> PROPS = new HashMap<>();
    private static boolean loaded = false;

    private SGProps() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        String explicit = System.getProperty("sg.props");
        File file = explicit != null ? new File(explicit) : resolve();
        if (file == null || !file.isFile()) {
            System.out.println("[PROPS] 没找到 props.txt，用内置默认值");
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf('=');
                if (i > 0) {
                    PROPS.put(line.substring(0, i).trim(), line.substring(i + 1));
                }
            }
        } catch (Exception e) {
            System.out.println("[PROPS] 读取失败: " + e);
            return;
        }
        System.out.println("[PROPS] 已加载 " + PROPS.size() + " 条真机属性 <- " + file);
    }

    private static File resolve() {
        for (String prefix : new String[]{"", "../", "../../", "../../../", "../../../../"}) {
            File f = new File(prefix + "tools/unidbg-tmall/props.txt");
            if (f.isFile()) {
                return f.getAbsoluteFile();
            }
        }
        return null;
    }

    /** 供 SystemPropertyProvider 调用；返回 null 表示交回默认实现。 */
    public static String get(String key) {
        ensureLoaded();
        String v = PROPS.get(key);
        if (v != null) {
            return v;
        }
        // 少数关键属性即使没导出也要有值，否则 SG 会认为信息缺失
        switch (key) {
            case "ro.product.model":
                return "PKR110";
            case "ro.product.brand":
            case "ro.product.manufacturer":
                return "OnePlus";
            case "ro.build.version.sdk":
                return "30";
            case "ro.build.version.release":
                return "11";
            default:
                return null;
        }
    }
}
