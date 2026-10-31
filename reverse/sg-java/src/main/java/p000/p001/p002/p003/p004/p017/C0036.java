package p000.p001.p002.p003.p004.p017;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.litevm.LiteVMParameterWrapper;
import java.util.concurrent.atomic.AtomicBoolean;

/* renamed from: а.а.а.а.а.ё.б, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
class C0036 {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f99;

    /* renamed from: б, reason: contains not printable characters */
    private volatile long f100;

    /* renamed from: в, reason: contains not printable characters */
    private AtomicBoolean f101 = new AtomicBoolean(false);

    private C0036(ISecurityGuardPlugin iSecurityGuardPlugin, long j, String str, String str2) {
        this.f100 = j;
        this.f99 = iSecurityGuardPlugin;
        this.f101.set(true);
    }

    /* renamed from: а, reason: contains not printable characters */
    static C0036 m134(ISecurityGuardPlugin iSecurityGuardPlugin, String str, String str2, byte[] bArr, long j) {
        return new C0036(iSecurityGuardPlugin, ((Long) iSecurityGuardPlugin.getRouter().doCommand(12501, new Object[]{str, str2, bArr, Long.valueOf(j)})).longValue(), str, str2);
    }

    /* renamed from: а, reason: contains not printable characters */
    static C0036 m135(ISecurityGuardPlugin iSecurityGuardPlugin, String str, String str2, byte[] bArr, Object[] objArr) {
        return new C0036(iSecurityGuardPlugin, ((Long) iSecurityGuardPlugin.getRouter().doCommand(12501, new Object[]{str, str2, bArr, 0L})).longValue(), str, str2);
    }

    /* renamed from: в, reason: contains not printable characters */
    private Long m136() {
        return Long.valueOf(this.f100);
    }

    /* renamed from: а, reason: contains not printable characters */
    Object m137(int i, LiteVMParameterWrapper[] liteVMParameterWrapperArr, int i2, int i3) throws SecException {
        if (!m140()) {
            throw new SecException("LVM instance not valid", 2101);
        }
        return this.f99.getRouter().doCommand(i3, new Object[]{Long.valueOf(m136().longValue()), Integer.valueOf(liteVMParameterWrapperArr != null ? liteVMParameterWrapperArr.length : 0), liteVMParameterWrapperArr, Integer.valueOf(i), Integer.valueOf(i2)});
    }

    /* renamed from: а, reason: contains not printable characters */
    void m138() throws SecException {
        if (!m140()) {
            throw new SecException("LVM instance not valid", 2101);
        }
        this.f101.set(false);
        this.f99.getRouter().doCommand(12503, new Object[]{Long.valueOf(m136().longValue())});
    }

    /* renamed from: а, reason: contains not printable characters */
    void m139(byte[] bArr) throws SecException {
        if (!m140()) {
            throw new SecException("LVM instance not valid", 2101);
        }
        this.f99.getRouter().doCommand(12502, new Object[]{Long.valueOf(m136().longValue()), bArr});
    }

    /* renamed from: б, reason: contains not printable characters */
    boolean m140() {
        return this.f101.get();
    }
}
