package p000.p018.p019.p020.p021.p026;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.sdk.safetoken.ISafeTokenComponent;

/* renamed from: а.б.а.а.а.д.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0041 implements ISafeTokenComponent {

    /* renamed from: а, reason: contains not printable characters */
    private com.alibaba.wireless.security.open.safetoken.ISafeTokenComponent f106;

    public C0041(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f106 = (com.alibaba.wireless.security.open.safetoken.ISafeTokenComponent) iSecurityGuardPlugin.getInterface(com.alibaba.wireless.security.open.safetoken.ISafeTokenComponent.class);
    }

    public byte[] decryptWithToken(String str, byte[] bArr, int i) {
        try {
            return this.f106.decryptWithToken(str, bArr, i);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] encryptWithToken(String str, byte[] bArr, int i) {
        try {
            return this.f106.encryptWithToken(str, bArr, i);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getOtp(String str, int i, String[] strArr, byte[][] bArr) {
        try {
            return this.f106.getOtp(str, i, strArr, bArr);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isTokenExisted(String str) {
        try {
            return this.f106.isTokenExisted(str);
        } catch (SecException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void removeToken(String str) {
        try {
            this.f106.removeToken(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public boolean saveToken(String str, String str2, String str3, int i) {
        try {
            return this.f106.saveToken(str, str2, str3, i);
        } catch (SecException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String signWithToken(String str, byte[] bArr, int i) {
        try {
            return this.f106.signWithToken(str, bArr, i);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }
}
