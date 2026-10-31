package p000.p001.p002.p003.p004.p005;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.atlasencrypt.IAtlasEncryptComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.io.UnsupportedEncodingException;

/* renamed from: а.а.а.а.а.а.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0022 implements IAtlasEncryptComponent {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f84;

    public C0022(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    /* renamed from: а, reason: contains not printable characters */
    private byte[] m96(String str, String str2, String str3) {
        return (byte[]) this.f84.getRouter().doCommand(11901, new Object[]{str, str2, str3});
    }

    public String atlasSafeEncrypt(String str, String str2) throws SecException {
        if (str == null || str.length() <= 0) {
            throw new SecException("", 1001);
        }
        byte[] m96 = m96("a", str, str2);
        if (m96 == null) {
            return null;
        }
        try {
            return new String(m96, "UTF-8");
        } catch (UnsupportedEncodingException unused) {
            return null;
        }
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f84 = iSecurityGuardPlugin;
        return 0;
    }
}
