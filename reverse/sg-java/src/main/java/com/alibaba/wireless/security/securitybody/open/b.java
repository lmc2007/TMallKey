package com.alibaba.wireless.security.securitybody.open;

import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class b implements ISecurityBodyComponent {

    /* renamed from: a, reason: collision with root package name */
    private ISecurityGuardPlugin f40a;

    public b(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
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

    private String a(HashMap<String, String> hashMap) {
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

    public boolean enterRiskScene(int i, HashMap<String, Object> hashMap) {
        return ((Boolean) this.f40a.getRouter().doCommand(20110, new Object[]{Integer.valueOf(i), hashMap})).booleanValue();
    }

    public String getSecurityBodyDataEx(String str, String str2, String str3, HashMap<String, String> hashMap, int i, int i2) {
        a(hashMap);
        return (String) this.f40a.getRouter().doCommand(20102, new Object[]{str, str2, Integer.valueOf(i), str3, a(hashMap), Integer.valueOf(i2)});
    }

    public String getSecurityBodyDataEx(String str, HashMap<String, String> hashMap, int i, int i2) {
        return (String) this.f40a.getRouter().doCommand(20102, new Object[]{null, null, Integer.valueOf(i), str, a(hashMap), Integer.valueOf(i2)});
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f40a = iSecurityGuardPlugin;
        return 0;
    }

    public boolean leaveRiskScene(int i) {
        return ((Boolean) this.f40a.getRouter().doCommand(20111, new Object[]{Integer.valueOf(i)})).booleanValue();
    }
}
