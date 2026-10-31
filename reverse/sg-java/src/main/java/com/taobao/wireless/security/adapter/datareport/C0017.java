package com.taobao.wireless.security.adapter.datareport;

import com.alibaba.wireless.security.framework.IRouterComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* renamed from: com.taobao.wireless.security.adapter.datareport.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0017 {

    /* renamed from: а, reason: contains not printable characters */
    private static IRouterComponent f68;

    /* renamed from: б, reason: contains not printable characters */
    private static Executor f69;

    /* renamed from: в, reason: contains not printable characters */
    private static Boolean f70;

    /* renamed from: com.taobao.wireless.security.adapter.datareport.а$а, reason: contains not printable characters */
    static class ThreadFactoryC0018 implements ThreadFactory {
        ThreadFactoryC0018() {
        }

        @Override // java.util.concurrent.ThreadFactory
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "SGNE");
        }
    }

    /* renamed from: com.taobao.wireless.security.adapter.datareport.а$б, reason: contains not printable characters */
    static class RunnableC0019 implements Runnable {

        /* renamed from: а, reason: contains not printable characters */
        final /* synthetic */ C0020 f71;

        RunnableC0019(C0020 c0020) {
            this.f71 = c0020;
        }

        @Override // java.lang.Runnable
        public void run() {
            try {
                C0017.f68.doCommand(13701, new Object[]{this.f71.m94(), Integer.valueOf(this.f71.m85()), Integer.valueOf(this.f71.m89()), this.f71.m80(), Integer.valueOf(this.f71.m91()), Integer.valueOf(this.f71.m92()), Long.valueOf(this.f71.m93()), Long.valueOf(this.f71.m95())});
            } catch (Throwable unused) {
            }
        }
    }

    /* renamed from: com.taobao.wireless.security.adapter.datareport.а$в, reason: contains not printable characters */
    public static class C0020 {

        /* renamed from: а, reason: contains not printable characters */
        String f72;

        /* renamed from: б, reason: contains not printable characters */
        int f73;

        /* renamed from: в, reason: contains not printable characters */
        String f74;

        /* renamed from: г, reason: contains not printable characters */
        int f75;

        /* renamed from: д, reason: contains not printable characters */
        int f76;

        /* renamed from: е, reason: contains not printable characters */
        int f77;

        /* renamed from: ж, reason: contains not printable characters */
        long f78;

        /* renamed from: ё, reason: contains not printable characters */
        long f79;

        /* renamed from: а, reason: contains not printable characters */
        public String m80() {
            return this.f74;
        }

        /* renamed from: а, reason: contains not printable characters */
        public void m81(int i) {
            this.f75 = i;
        }

        /* renamed from: а, reason: contains not printable characters */
        public void m82(long j) {
            this.f79 = j;
        }

        /* renamed from: а, reason: contains not printable characters */
        public void m83(EnumC0021 enumC0021) {
            this.f73 = enumC0021.ordinal();
        }

        /* renamed from: а, reason: contains not printable characters */
        public void m84(String str) {
            this.f74 = str;
        }

        /* renamed from: б, reason: contains not printable characters */
        public int m85() {
            return this.f75;
        }

        /* renamed from: б, reason: contains not printable characters */
        public void m86(int i) {
            this.f76 = i;
        }

        /* renamed from: б, reason: contains not printable characters */
        public void m87(long j) {
            this.f78 = j;
        }

        /* renamed from: б, reason: contains not printable characters */
        public void m88(String str) {
            this.f72 = str;
        }

        /* renamed from: в, reason: contains not printable characters */
        public int m89() {
            return this.f73;
        }

        /* renamed from: в, reason: contains not printable characters */
        public void m90(int i) {
            this.f77 = i;
        }

        /* renamed from: г, reason: contains not printable characters */
        public int m91() {
            return this.f76;
        }

        /* renamed from: д, reason: contains not printable characters */
        public int m92() {
            return this.f77;
        }

        /* renamed from: е, reason: contains not printable characters */
        public long m93() {
            return this.f79;
        }

        /* renamed from: ж, reason: contains not printable characters */
        public String m94() {
            return this.f72;
        }

        /* renamed from: ё, reason: contains not printable characters */
        public long m95() {
            return this.f78;
        }
    }

    /* renamed from: com.taobao.wireless.security.adapter.datareport.а$г, reason: contains not printable characters */
    public enum EnumC0021 {
        https,
        mtop,
        unknown
    }

    /* renamed from: а, reason: contains not printable characters */
    public static void m75(ISecurityGuardPlugin iSecurityGuardPlugin) {
        f68 = iSecurityGuardPlugin.getRouter();
        f69 = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque(30), new ThreadFactoryC0018(), new ThreadPoolExecutor.DiscardPolicy());
    }

    /* renamed from: а, reason: contains not printable characters */
    public static void m76(C0020 c0020) {
        if (f68 == null || f69 == null || c0020 == null || !m77()) {
            return;
        }
        f69.execute(new RunnableC0019(c0020));
    }

    /* renamed from: а, reason: contains not printable characters */
    private static boolean m77() {
        IRouterComponent iRouterComponent = f68;
        if (iRouterComponent == null) {
            return false;
        }
        if (f70 == null) {
            try {
                Integer num = (Integer) iRouterComponent.doCommand(13702, new Object[0]);
                if (num != null) {
                    f70 = Boolean.valueOf(num.equals(1));
                }
            } catch (Throwable unused) {
            }
        }
        Boolean bool = f70;
        if (bool != null) {
            return bool.booleanValue();
        }
        return false;
    }

    /* renamed from: в, reason: contains not printable characters */
    public static boolean m79() {
        return f68 != null && m77();
    }
}
