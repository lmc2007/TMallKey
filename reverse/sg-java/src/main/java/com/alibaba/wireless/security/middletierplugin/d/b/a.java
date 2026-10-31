package com.alibaba.wireless.security.middletierplugin.d.b;

import android.content.Context;
import com.alibaba.wireless.security.middletierplugin.b;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.SecurityGuardManager;
import com.alibaba.wireless.security.open.SecurityGuardParamContext;
import com.alibaba.wireless.security.open.avmp.IAVMPGenericComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class a {
    private static Context b = null;
    private static Object c = null;
    private static a d = null;
    private static String e = "";
    private static String f = "mwua";
    private static String g = "sgcipher2";
    private static InvocationHandler h = new C0003a();
    private IAVMPGenericComponent.IAVMPGenericInstance a = null;

    /* renamed from: com.alibaba.wireless.security.middletierplugin.d.b.a$a, reason: collision with other inner class name */
    static class C0003a implements InvocationHandler {
        C0003a() {
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            char c;
            String name = method.getName();
            switch (name.hashCode()) {
                case -1249346035:
                    if (name.equals("getWua")) {
                        c = 3;
                        break;
                    }
                    c = 65535;
                    break;
                case -75151821:
                    if (name.equals("getSign")) {
                        c = 1;
                        break;
                    }
                    c = 65535;
                    break;
                case 3237136:
                    if (name.equals("init")) {
                        c = 0;
                        break;
                    }
                    c = 65535;
                    break;
                case 2101377334:
                    if (name.equals("getMiniWua")) {
                        c = 2;
                        break;
                    }
                    c = 65535;
                    break;
                default:
                    c = 65535;
                    break;
            }
            if (c == 0) {
                return Boolean.valueOf(a.d.d((HashMap) objArr[0]));
            }
            if (c == 1) {
                return a.d.b((HashMap) objArr[0]);
            }
            if (c == 2) {
                return a.d.a((HashMap) objArr[0]);
            }
            if (c != 3) {
                return null;
            }
            return a.d.c((HashMap) objArr[0]);
        }
    }

    private a(ISecurityGuardPlugin iSecurityGuardPlugin) {
    }

    public static Object a(Class cls, ISecurityGuardPlugin iSecurityGuardPlugin, Context context) {
        if (c == null) {
            synchronized (a.class) {
                if (c == null) {
                    c = b.a(cls, h);
                    d = new a(iSecurityGuardPlugin);
                    b = context;
                }
            }
        }
        return c;
    }

    private String a(byte[] bArr, int i) {
        synchronized (a.class) {
            if (!b()) {
                return null;
            }
            byte[] bArr2 = (byte[]) this.a.invokeAVMP2("sign_v2", byte[].class, new Object[]{"vs_config_0", bArr, Integer.valueOf(i), null, null});
            if (bArr2 == null) {
                return null;
            }
            try {
                return new String(bArr2, "UTF-8");
            } catch (UnsupportedEncodingException unused) {
                throw new SecException(2398);
            }
        }
    }

    private String a(byte[] bArr, int i, HashMap<String, Object> hashMap) {
        try {
            String str = new String(bArr, "UTF-8");
            String str2 = (String) hashMap.get("appkey");
            if (str2 == null || str2.length() <= 0) {
                throw new SecException(2302);
            }
            HashMap hashMap2 = new HashMap();
            hashMap2.put("INPUT", str);
            if (i == 2) {
                hashMap2.put("ATLAS", "daily");
            }
            SecurityGuardParamContext securityGuardParamContext = new SecurityGuardParamContext();
            securityGuardParamContext.appKey = str2;
            securityGuardParamContext.requestType = 7;
            securityGuardParamContext.paramMap = hashMap2;
            return SecurityGuardManager.getInstance(b).getSecureSignatureComp().signRequest(securityGuardParamContext, e);
        } catch (UnsupportedEncodingException unused) {
            throw new SecException(2304);
        }
    }

    private boolean b() {
        if (this.a != null) {
            return true;
        }
        synchronized (a.class) {
            if (this.a != null) {
                return true;
            }
            this.a = ((IAVMPGenericComponent) SecurityGuardManager.getInstance(b).getInterface(IAVMPGenericComponent.class)).createAVMPInstance(f, g);
            return this.a != null;
        }
    }

    public HashMap<String, String> a(HashMap<String, Object> hashMap) {
        HashMap hashMap2;
        int i;
        if (hashMap == null || !hashMap.isEmpty()) {
            hashMap2 = null;
            i = 0;
        } else {
            int intValue = hashMap.containsKey("env") ? ((Integer) hashMap.get("env")).intValue() : 0;
            if (hashMap.containsKey("extend_paras")) {
                i = intValue;
                hashMap2 = (HashMap) hashMap.get("extend_paras");
            } else {
                hashMap2 = null;
                i = intValue;
            }
        }
        if (i != 0 && i != 1 && i != 2) {
            throw new SecException(2301);
        }
        if (!d(hashMap)) {
            throw new SecException(2303);
        }
        String securityBodyDataEx = ((ISecurityBodyComponent) SecurityGuardManager.getInstance(b).getInterface(ISecurityBodyComponent.class)).getSecurityBodyDataEx((String) null, (String) null, (String) null, hashMap2, 8, i);
        if (securityBodyDataEx == null) {
            return null;
        }
        HashMap<String, String> hashMap3 = new HashMap<>();
        hashMap3.put("x-miniwua", securityBodyDataEx);
        return hashMap3;
    }

    public HashMap<String, String> b(HashMap<String, Object> hashMap) {
        if (hashMap == null || hashMap.isEmpty()) {
            throw new SecException(2301);
        }
        byte[] bArr = (byte[]) hashMap.get("data");
        int intValue = hashMap.containsKey("env") ? ((Integer) hashMap.get("env")).intValue() : 0;
        if (bArr == null || bArr.length <= 0 || !(intValue == 0 || intValue == 1 || intValue == 2)) {
            throw new SecException(2301);
        }
        if (!d(hashMap)) {
            throw new SecException(2303);
        }
        String a = a(bArr, intValue, hashMap);
        if (a == null) {
            return null;
        }
        HashMap<String, String> hashMap2 = new HashMap<>();
        hashMap2.put("x-sign", a);
        return hashMap2;
    }

    public HashMap<String, String> c(HashMap<String, Object> hashMap) {
        if (hashMap == null || hashMap.isEmpty()) {
            throw new SecException(2301);
        }
        byte[] bArr = (byte[]) hashMap.get("data");
        int intValue = hashMap.containsKey("env") ? ((Integer) hashMap.get("env")).intValue() : 0;
        if (bArr == null || bArr.length <= 0 || !(intValue == 0 || intValue == 1 || intValue == 2)) {
            throw new SecException(2301);
        }
        if (!d(hashMap)) {
            throw new SecException(2303);
        }
        String a = a(bArr, intValue);
        if (a == null) {
            return null;
        }
        HashMap<String, String> hashMap2 = new HashMap<>();
        hashMap2.put("wua", a);
        return hashMap2;
    }

    public boolean d(HashMap<String, Object> hashMap) {
        String str;
        if (this.a != null) {
            return true;
        }
        synchronized (a.class) {
            if (hashMap != null) {
                if (!hashMap.isEmpty() && hashMap.containsKey("auth_code") && (str = (String) hashMap.get("auth_code")) != null && str.length() > 0) {
                    e = str;
                    f = str + "_mwua";
                }
            }
        }
        return true;
    }
}
