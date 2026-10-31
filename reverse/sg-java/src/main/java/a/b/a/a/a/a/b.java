package a.b.a.a.a.a;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.sdk.securitybody.ISecurityBodyComponent;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class b implements ISecurityBodyComponent {

    /* renamed from: a, reason: collision with root package name */
    private ISecurityGuardPlugin f2a;
    private com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent b;

    public b(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f2a = iSecurityGuardPlugin;
        if (iSecurityGuardPlugin != null) {
            this.b = (com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent) iSecurityGuardPlugin.getInterface(com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent.class);
        }
    }

    public String getSecurityBodyData(String str) {
        return getSecurityBodyDataEx(str, null, 0);
    }

    public String getSecurityBodyData(String str, String str2) {
        return getSecurityBodyDataEx(str, str2, 0);
    }

    public String getSecurityBodyDataEx(String str, String str2, int i) {
        com.alibaba.wireless.security.securitybody.a.b("getSecurityBodyDataEx");
        try {
            String securityBodyDataEx = this.b.getSecurityBodyDataEx(str, str2, "", (HashMap) null, i, 3);
            com.alibaba.wireless.security.securitybody.a.a("getSecurityBodyDataEx");
            return securityBodyDataEx;
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean initSecurityBody(String str) {
        if (str == null) {
            return false;
        }
        return ((Boolean) this.f2a.getRouter().doCommand(20101, new Object[]{str, this.f2a.getContext().getPackageName()})).booleanValue();
    }

    public boolean putUserActionRecord(String str, String str2, float f, float f2) {
        if (str == null || str2 == null) {
            return false;
        }
        return ((Boolean) this.f2a.getRouter().doCommand(20104, new Object[]{str, str2, Float.valueOf(f), Float.valueOf(f2)})).booleanValue();
    }

    public boolean putUserTrackRecord(String str) {
        if (str == null) {
            return false;
        }
        return ((Boolean) this.f2a.getRouter().doCommand(20103, new Object[]{str})).booleanValue();
    }

    public void setSecurityBodyServer(int i) {
        this.f2a.getRouter().doCommand(20105, new Object[]{Integer.valueOf(i)});
    }
}
