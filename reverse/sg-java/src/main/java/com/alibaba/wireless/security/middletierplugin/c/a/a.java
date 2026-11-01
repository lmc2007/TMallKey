package com.alibaba.wireless.security.middletierplugin.c.a;

import android.content.Context;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/** 裁剪版桩：原类做 WebView cookie 嗅探（FC/ES 风控），与 getSecurityFactors 无关。 */
public class a {
    public static void a(ISecurityGuardPlugin plugin, Context context) {
        System.out.println("[MT] c.a.a.a(plugin, ctx) 桩");
    }
}
