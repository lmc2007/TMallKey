package p000.p018.p019.p020.p021.p023;

import com.alibaba.wireless.security.mainplugin.SecurityGuardMainPlugin;
import com.taobao.wireless.security.adapter.datacollection.C0011;
import com.taobao.wireless.security.sdk.datacollection.IDataCollectionComponent;

/* renamed from: а.б.а.а.а.б.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0038 implements IDataCollectionComponent {

    /* renamed from: а, reason: contains not printable characters */
    private C0011 f103;

    public C0038(SecurityGuardMainPlugin securityGuardMainPlugin) {
        this.f103 = C0011.m44(securityGuardMainPlugin);
    }

    public String getEncryptedDevAndEnvInfo(int i, String str) {
        return null;
    }

    public String getNick() {
        try {
            return this.f103.m48();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getNickEx(int i) {
        try {
            return this.f103.m49(i);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean setNick(String str) {
        try {
            return this.f103.m51(str);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setNickEx(int i, String str) {
        try {
            return this.f103.m50(i, str);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
