package p000.p018.p019.p020.p021.p029;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.sdk.staticdataencrypt.IStaticDataEncryptComponent;

/* renamed from: а.б.а.а.а.ё.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0044 implements IStaticDataEncryptComponent {

    /* renamed from: а, reason: contains not printable characters */
    private com.alibaba.wireless.security.open.staticdataencrypt.IStaticDataEncryptComponent f109;

    public C0044(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f109 = (com.alibaba.wireless.security.open.staticdataencrypt.IStaticDataEncryptComponent) iSecurityGuardPlugin.getInterface(com.alibaba.wireless.security.open.staticdataencrypt.IStaticDataEncryptComponent.class);
    }

    public byte[] staticBinarySafeDecrypt(int i, String str, byte[] bArr) {
        try {
            return this.f109.staticBinarySafeDecrypt(i, str, bArr, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] staticBinarySafeDecryptNoB64(int i, String str, byte[] bArr) {
        try {
            return this.f109.staticBinarySafeDecryptNoB64(i, str, bArr, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] staticBinarySafeEncrypt(int i, String str, byte[] bArr) {
        try {
            return this.f109.staticBinarySafeEncrypt(i, str, bArr, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] staticBinarySafeEncryptNoB64(int i, String str, byte[] bArr) {
        try {
            return this.f109.staticBinarySafeEncryptNoB64(i, str, bArr, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String staticSafeDecrypt(int i, String str, String str2) {
        try {
            return this.f109.staticSafeDecrypt(i, str, str2, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String staticSafeEncrypt(int i, String str, String str2) {
        try {
            return this.f109.staticSafeEncrypt(i, str, str2, "");
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }
}
