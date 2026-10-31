package p000.p001.p002.p003.p004.p008;

import com.alibaba.wireless.security.open.datacollection.IDataCollectionComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.adapter.datacollection.C0011;

/* renamed from: а.а.а.а.а.г.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0025 implements IDataCollectionComponent {

    /* renamed from: а, reason: contains not printable characters */
    private C0011 f87;

    public C0025(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    public String getNick() {
        return this.f87.m48();
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f87 = C0011.m44(iSecurityGuardPlugin);
        return 0;
    }

    public boolean setNick(String str) {
        return this.f87.m51(str);
    }
}
