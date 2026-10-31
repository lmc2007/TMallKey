package a.b.a.a.a.a;

import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.sdk.rootdetect.IRootDetectComponent;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class a implements IRootDetectComponent {

    /* renamed from: a, reason: collision with root package name */
    ISecurityGuardPlugin f1a;

    public a(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f1a = iSecurityGuardPlugin;
    }

    public boolean isRoot() {
        return ((Boolean) this.f1a.getRouter().doCommand(10701, new Object[0])).booleanValue();
    }
}
