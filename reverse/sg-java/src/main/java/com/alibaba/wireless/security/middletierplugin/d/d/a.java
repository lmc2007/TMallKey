package com.alibaba.wireless.security.middletierplugin.d.d;

import android.content.Context;
import com.alibaba.wireless.security.middletierplugin.b;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class a {
    /** 原来叫 a，和类名 a 冲突（编译器当成字段访问），改名 */
    private static Object PROXY;
    private static ISecurityGuardPlugin b;
    private static a c;
    private static InvocationHandler d = new C0005a();

    /* renamed from: com.alibaba.wireless.security.middletierplugin.d.d.a$a, reason: collision with other inner class name */
    static class C0005a implements InvocationHandler {
        C0005a() {
        }

        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) throws Throwable {
            char sel;
            String name = method.getName();
            int hashCode = name.hashCode();
            // jadx 把 switch 的 break 丢了，导致 sel 被无条件覆盖 → 任何调用都 return null。
            // 按原意（tablesswitch 的 default 分支）补成 else。
            if (hashCode != 3237136) {
                if (hashCode == 165313390 && name.equals("getSecurityFactors")) {
                    sel = 1;
                } else {
                    sel = 65535;
                }
            } else {
                if (name.equals("init")) {
                    sel = 0;
                } else {
                    sel = 65535;
                }
            }
            if (sel == 0) {
                c.b((HashMap) objArr[0]);
                return null;
            }
            if (sel != 1) {
                return null;
            }
            return c.a((HashMap<String, Object>) objArr[0]);
        }
    }

    private a(ISecurityGuardPlugin iSecurityGuardPlugin) {
        b = iSecurityGuardPlugin;
    }

    public static Object a(Class cls, ISecurityGuardPlugin iSecurityGuardPlugin, Context context) {
        if (PROXY == null) {
            synchronized (a.class) {
                if (PROXY == null) {
                    // 这里的 b 原本指工具类 com...middletierplugin.b，被同名字段 b 遮蔽了
                    PROXY = com.alibaba.wireless.security.middletierplugin.b.a(cls, d);
                    c = new a(iSecurityGuardPlugin);
                }
            }
        }
        return PROXY;
    }

    private String a(String str) {
        if (str != null) {
            try {
                return URLEncoder.encode(str, "UTF-8");
            } catch (Exception unused) {
            }
        }
        return null;
    }

    private String c(HashMap<String, String> hashMap) {
        String a2;
        StringBuilder sb = new StringBuilder();
        if (hashMap != null) {
            Iterator<String> it = hashMap.keySet().iterator();
            while (it.hasNext()) {
                String a3 = a(it.next());
                if (a3 != null && a3.length() > 0 && (a2 = a(hashMap.get(a3))) != null) {
                    if (sb.length() != 0) {
                        sb.append("&");
                    }
                    sb.append(a3);
                    sb.append("=");
                    sb.append(a2);
                }
            }
        }
        return sb.toString();
    }

    public HashMap<String, String> a(HashMap<String, Object> hashMap) throws SecException {
        if (hashMap == null || hashMap.isEmpty()) {
            throw new SecException(2401);
        }
        if (hashMap.isEmpty() || !hashMap.containsKey("appkey") || !hashMap.containsKey("data") || !hashMap.containsKey("useWua")) {
            throw new SecException(2401);
        }
        com.alibaba.wireless.security.middletierplugin.a.b("getSecurityFactors");
        String str = (String) hashMap.get("appkey");
        String str2 = (String) hashMap.get("data");
        boolean booleanValue = ((Boolean) hashMap.get("useWua")).booleanValue();
        HashMap<String, String> hashMap2 = (HashMap) b.getRouter().doCommand(70102, new Object[]{str, str2, Boolean.valueOf(booleanValue), Integer.valueOf(hashMap.containsKey("env") ? ((Integer) hashMap.get("env")).intValue() : 0), hashMap.containsKey("api") ? (String) hashMap.get("api") : null, hashMap.containsKey("extendParas") ? c((HashMap) hashMap.get("extendParas")) : null, (String) hashMap.get("authCode"), (String) hashMap.get("signKey"), hashMap.containsKey("miniWua") ? (String) hashMap.get("miniWua") : null, hashMap.containsKey("requestId") ? (String) hashMap.get("requestId") : null, Integer.valueOf(hashMap.containsKey("bizId") ? ((Integer) hashMap.get("bizId")).intValue() : 0), Integer.valueOf(hashMap.containsKey("flag") ? ((Integer) hashMap.get("flag")).intValue() : 0)});
        com.alibaba.wireless.security.middletierplugin.a.a("getSecurityFactors");
        return hashMap2;
    }

    public void b(HashMap<String, Object> hashMap) {
        b.getRouter().doCommand(70101, new Object[]{(hashMap == null || hashMap.isEmpty() || !hashMap.containsKey("authCode")) ? "" : (String) hashMap.get("authCode"), Integer.valueOf(hashMap.containsKey("flag") ? ((Integer) hashMap.get("flag")).intValue() : 0)});
    }
}
