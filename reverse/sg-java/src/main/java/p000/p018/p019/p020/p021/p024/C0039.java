package p000.p018.p019.p020.p021.p024;

import com.alibaba.wireless.security.mainplugin.SecurityGuardMainPlugin;
import com.alibaba.wireless.security.open.SecException;
import com.taobao.wireless.security.sdk.dynamicdataencrypt.IDynamicDataEncryptComponent;

/* renamed from: а.б.а.а.а.в.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0039 implements IDynamicDataEncryptComponent {

    /* renamed from: а, reason: contains not printable characters */
    private com.alibaba.wireless.security.open.dynamicdataencrypt.IDynamicDataEncryptComponent f104;

    public C0039(SecurityGuardMainPlugin securityGuardMainPlugin) {
        this.f104 = (com.alibaba.wireless.security.open.dynamicdataencrypt.IDynamicDataEncryptComponent) securityGuardMainPlugin.getInterface(com.alibaba.wireless.security.open.dynamicdataencrypt.IDynamicDataEncryptComponent.class);
    }

    public String dynamicDecrypt(String str) {
        try {
            return this.f104.dynamicDecrypt(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String dynamicDecryptDDp(String str) {
        try {
            return this.f104.dynamicDecryptDDp(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String dynamicEncrypt(String str) {
        try {
            return this.f104.dynamicEncrypt(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String dynamicEncryptDDp(String str) {
        try {
            return this.f104.dynamicEncryptDDp(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }
}
