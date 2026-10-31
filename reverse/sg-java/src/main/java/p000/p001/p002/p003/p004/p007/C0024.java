package p000.p001.p002.p003.p004.p007;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.compat.ICompatComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/* renamed from: а.а.а.а.а.в.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0024 implements ICompatComponent {

    /* renamed from: а, reason: contains not printable characters */
    private static ISecurityGuardPlugin f86;

    public C0024(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    /* renamed from: а, reason: contains not printable characters */
    private String m99(int i) {
        return (String) f86.getRouter().doCommand(12801, new Object[]{Integer.valueOf(i)});
    }

    public String getCachedSecurityToken(int i) throws SecException {
        String m99 = m99(i);
        return (m99 == null || "".equals(m99) || !(m99.length() == 32 || m99.length() == 24)) ? "000000000000000000000000" : m99;
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        f86 = iSecurityGuardPlugin;
        return 0;
    }
}
