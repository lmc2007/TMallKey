package com.alibaba.wireless.security.securitybody;

import com.alibaba.wireless.security.framework.IRouterComponent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class e {

    /* renamed from: a, reason: collision with root package name */
    private static final boolean f35a = false;
    private static final String b = "SecurityBodyUTCallback";
    private static boolean c = false;
    private static final String d = "YTEC";
    private static IRouterComponent e;

    static class a implements InvocationHandler {

        /* renamed from: a, reason: collision with root package name */
        final /* synthetic */ int[] f36a;

        a(int[] iArr) {
            this.f36a = iArr;
        }

        /* JADX WARN: Code restructure failed: missing block: B:11:0x003c, code lost:
        
            if (r0 == 1) goto L24;
         */
        /* JADX WARN: Code restructure failed: missing block: B:12:0x003e, code lost:
        
            if (r0 == 2) goto L23;
         */
        /* JADX WARN: Code restructure failed: missing block: B:14:?, code lost:
        
            return null;
         */
        /* JADX WARN: Code restructure failed: missing block: B:17:?, code lost:
        
            return com.alibaba.wireless.security.securitybody.e.d;
         */
        /* JADX WARN: Code restructure failed: missing block: B:19:0x0046, code lost:
        
            if (r9.length <= 4) goto L38;
         */
        /* JADX WARN: Code restructure failed: missing block: B:20:0x0048, code lost:
        
            r8 = new java.util.HashMap();
            r8.put("eventID", java.lang.String.valueOf(r9[1]));
            r8.put("packageName", java.lang.String.valueOf(r9[0]));
            r8.put("arg1", java.lang.String.valueOf(r9[2]));
            r8.put("arg2", java.lang.String.valueOf(r9[3]));
            r8.put("arg3", java.lang.String.valueOf(r9[4]));
         */
        /* JADX WARN: Code restructure failed: missing block: B:22:0x0085, code lost:
        
            com.alibaba.wireless.security.securitybody.e.b(new org.json.JSONObject(r8).toString());
         */
        /* JADX WARN: Code restructure failed: missing block: B:27:?, code lost:
        
            return null;
         */
        @Override // java.lang.reflect.InvocationHandler
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public java.lang.Object invoke(java.lang.Object r7, java.lang.reflect.Method r8, java.lang.Object[] r9) {
            /*
                r6 = this;
                r7 = 0
                java.lang.String r8 = r8.getName()     // Catch: java.lang.Throwable -> L95
                r0 = -1
                int r1 = r8.hashCode()     // Catch: java.lang.Throwable -> L95
                r2 = -1377993658(0xffffffffaddd7c46, float:-2.517998E-11)
                r3 = 0
                r4 = 2
                r5 = 1
                if (r1 == r2) goto L31
                r2 = -767441515(0xffffffffd241c595, float:-2.0806089E11)
                if (r1 == r2) goto L27
                r2 = -684964780(0xffffffffd72c4454, float:-1.8940947E14)
                if (r1 == r2) goto L1d
                goto L3a
            L1d:
                java.lang.String r1 = "getPluginName"
                boolean r8 = r8.equals(r1)     // Catch: java.lang.Throwable -> L95
                if (r8 == 0) goto L3a
                r0 = 2
                goto L3a
            L27:
                java.lang.String r1 = "onEventDispatch"
                boolean r8 = r8.equals(r1)     // Catch: java.lang.Throwable -> L95
                if (r8 == 0) goto L3a
                r0 = 1
                goto L3a
            L31:
                java.lang.String r1 = "getAttentionEventIds"
                boolean r8 = r8.equals(r1)     // Catch: java.lang.Throwable -> L95
                if (r8 == 0) goto L3a
                r0 = 0
            L3a:
                if (r0 == 0) goto L93
                if (r0 == r5) goto L44
                if (r0 == r4) goto L41
                goto L95
            L41:
                java.lang.String r7 = "YTEC"
                goto L95
            L44:
                int r8 = r9.length     // Catch: java.lang.Throwable -> L95
                r0 = 4
                if (r8 <= r0) goto L95
                java.util.HashMap r8 = new java.util.HashMap     // Catch: java.lang.Throwable -> L95
                r8.<init>()     // Catch: java.lang.Throwable -> L95
                java.lang.String r1 = "eventID"
                r2 = r9[r5]     // Catch: java.lang.Throwable -> L95
                java.lang.String r2 = java.lang.String.valueOf(r2)     // Catch: java.lang.Throwable -> L95
                r8.put(r1, r2)     // Catch: java.lang.Throwable -> L95
                java.lang.String r1 = "packageName"
                r2 = r9[r3]     // Catch: java.lang.Throwable -> L95
                java.lang.String r2 = java.lang.String.valueOf(r2)     // Catch: java.lang.Throwable -> L95
                r8.put(r1, r2)     // Catch: java.lang.Throwable -> L95
                java.lang.String r1 = "arg1"
                r2 = r9[r4]     // Catch: java.lang.Throwable -> L95
                java.lang.String r2 = java.lang.String.valueOf(r2)     // Catch: java.lang.Throwable -> L95
                r8.put(r1, r2)     // Catch: java.lang.Throwable -> L95
                java.lang.String r1 = "arg2"
                r2 = 3
                r2 = r9[r2]     // Catch: java.lang.Throwable -> L95
                java.lang.String r2 = java.lang.String.valueOf(r2)     // Catch: java.lang.Throwable -> L95
                r8.put(r1, r2)     // Catch: java.lang.Throwable -> L95
                java.lang.String r1 = "arg3"
                r9 = r9[r0]     // Catch: java.lang.Throwable -> L95
                java.lang.String r9 = java.lang.String.valueOf(r9)     // Catch: java.lang.Throwable -> L95
                r8.put(r1, r9)     // Catch: java.lang.Throwable -> L95
                org.json.JSONObject r7 = new org.json.JSONObject     // Catch: java.lang.Throwable -> L91
                r7.<init>(r8)     // Catch: java.lang.Throwable -> L91
                java.lang.String r7 = r7.toString()     // Catch: java.lang.Throwable -> L91
                com.alibaba.wireless.security.securitybody.e.a(r7)     // Catch: java.lang.Throwable -> L91
            L91:
                r7 = r8
                goto L95
            L93:
                int[] r7 = r6.f36a     // Catch: java.lang.Throwable -> L95
            L95:
                return r7
            */
            throw new UnsupportedOperationException("Method not decompiled: com.alibaba.wireless.security.securitybody.e.a.invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[]):java.lang.Object");
        }
    }

    public static synchronized void a(IRouterComponent iRouterComponent) {
        Class<?> cls;
        Class<?> cls2;
        Method method;
        synchronized (e.class) {
            if (c) {
                return;
            }
            e = iRouterComponent;
            c = true;
            if (b()) {
                int[] a2 = a();
                if (a2 == null) {
                    return;
                }
                try {
                    cls = Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.usertrack.UserTrackAdaptor");
                    try {
                        cls2 = Class.forName("com.alibaba.wireless.security.open.securityguardaccsadapter.usertrack.IUserTrackPlugin");
                    } catch (Throwable unused) {
                        cls2 = null;
                    }
                } catch (Throwable unused2) {
                    cls = null;
                    cls2 = null;
                }
                try {
                    method = cls.getDeclaredMethod("registerListener", cls2);
                } catch (Throwable unused3) {
                    method = null;
                    if (cls != null) {
                    }
                    return;
                }
                if (cls != null || method == null || cls2 == null) {
                    return;
                }
                try {
                    method.invoke(null, Proxy.newProxyInstance(cls2.getClassLoader(), new Class[]{cls2}, new a(a2)));
                } catch (Throwable unused4) {
                }
            }
        }
    }

    private static int[] a() {
        IRouterComponent iRouterComponent = e;
        if (iRouterComponent == null) {
            return null;
        }
        try {
            return (int[]) iRouterComponent.doCommand(20802, new Object[]{0});
        } catch (Throwable unused) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void b(String str) {
        IRouterComponent iRouterComponent = e;
        if (iRouterComponent != null) {
            try {
                iRouterComponent.doCommand(20801, new Object[]{0, str});
            } catch (Throwable unused) {
            }
        }
    }

    private static boolean b() {
        IRouterComponent iRouterComponent = e;
        if (iRouterComponent != null) {
            try {
                return ((Boolean) iRouterComponent.doCommand(20803, new Object[]{0})).booleanValue();
            } catch (Throwable unused) {
            }
        }
        return false;
    }
}
