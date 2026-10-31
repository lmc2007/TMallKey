package p000.p018.p019.p020.p021.p022;

import com.alibaba.wireless.security.mainplugin.SecurityGuardMainPlugin;
import com.alibaba.wireless.security.open.SecException;
import com.taobao.wireless.security.sdk.atlasencrypt.IAtlasEncryptComponent;

/* renamed from: а.б.а.а.а.а.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0037 implements IAtlasEncryptComponent {

    /* renamed from: а, reason: contains not printable characters */
    private com.alibaba.wireless.security.open.atlasencrypt.IAtlasEncryptComponent f102;

    public C0037(SecurityGuardMainPlugin securityGuardMainPlugin) {
        this.f102 = (com.alibaba.wireless.security.open.atlasencrypt.IAtlasEncryptComponent) securityGuardMainPlugin.getInterface(com.alibaba.wireless.security.open.atlasencrypt.IAtlasEncryptComponent.class);
    }

    public String atlasSafeEncrypt(String str) {
        try {
            return this.f102.atlasSafeEncrypt(str, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }
}
