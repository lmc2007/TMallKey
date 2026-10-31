package p000.p001.p002.p003.p004.p015;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.staticdatastore.IStaticDataStoreComponent;

/* renamed from: а.а.а.а.а.к.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0032 implements IStaticDataStoreComponent {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f94 = null;

    public C0032(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    public String getAppKeyByIndex(int i, String str) throws SecException {
        return (String) this.f94.getRouter().doCommand(10602, new Object[]{Integer.valueOf(i), str});
    }

    public String getExtraData(String str, String str2) throws SecException {
        return (String) this.f94.getRouter().doCommand(10603, new Object[]{str, str2});
    }

    public int getKeyType(String str, String str2) throws SecException {
        return ((Integer) this.f94.getRouter().doCommand(10604, new Object[]{str, str2})).intValue();
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f94 = iSecurityGuardPlugin;
        return 0;
    }
}
