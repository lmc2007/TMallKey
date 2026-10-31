package p000.p001.p002.p003.p004.p009;

import com.alibaba.wireless.security.mainplugin.C0000;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.dynamicdataencrypt.IDynamicDataEncryptComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import p000.p001.p002.p003.p004.p006.C0023;

/* renamed from: а.а.а.а.а.д.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0026 implements IDynamicDataEncryptComponent {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f88 = null;

    public C0026(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    /* renamed from: а, reason: contains not printable characters */
    private String m100(int i, String str, boolean z) throws SecException {
        if (str == null || str.length() <= 0) {
            throw new SecException("Input data string is empty", 401);
        }
        byte[] bArr = (byte[]) this.f88.getRouter().doCommand(10501, new Object[]{Integer.valueOf(i), Boolean.valueOf(z), str});
        if (bArr != null) {
            try {
                if (bArr.length > 0) {
                    return new String(bArr, "UTF-8");
                }
            } catch (Exception unused) {
                throw new SecException("dynamic crypt return with invalid string data", 499);
            }
        }
        throw new SecException("dynamic crypt return with null data", 499);
    }

    /* renamed from: а, reason: contains not printable characters */
    private byte[] m101(int i, byte[] bArr, boolean z) throws SecException {
        if (bArr == null || bArr.length <= 0) {
            throw new SecException("Input byte data is empty", 401);
        }
        return (byte[]) this.f88.getRouter().doCommand(10501, new Object[]{Integer.valueOf(i), Boolean.valueOf(z), C0023.m97(bArr)});
    }

    public String dynamicDecrypt(String str) throws SecException {
        return m100(2, str, false);
    }

    public byte[] dynamicDecryptByteArray(byte[] bArr) throws SecException {
        if (bArr == null || bArr.length <= 0) {
            throw new SecException(401);
        }
        return C0023.m98(m100(2, new String(bArr), false));
    }

    public byte[] dynamicDecryptByteArrayDDp(byte[] bArr) throws SecException {
        if (bArr == null || bArr.length <= 0) {
            throw new SecException(401);
        }
        return C0023.m98(m100(4, new String(bArr), true));
    }

    public String dynamicDecryptDDp(String str) throws SecException {
        C0000.m4("dynamicDecryptDDp");
        String m100 = m100(4, str, true);
        C0000.m3("dynamicDecryptDDp");
        return m100;
    }

    public String dynamicEncrypt(String str) throws SecException {
        return m100(1, str, false);
    }

    public byte[] dynamicEncryptByteArray(byte[] bArr) throws SecException {
        return m101(1, bArr, false);
    }

    public byte[] dynamicEncryptByteArrayDDp(byte[] bArr) throws SecException {
        return m101(3, bArr, true);
    }

    public String dynamicEncryptDDp(String str) throws SecException {
        C0000.m4("dynamicEncryptDDp");
        String m100 = m100(3, str, true);
        C0000.m3("dynamicEncryptDDp");
        return m100;
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f88 = iSecurityGuardPlugin;
        return 0;
    }

    public boolean isVerifyCrypt(String str) throws SecException {
        if (str == null || str.length() <= 0) {
            throw new SecException(401);
        }
        return str.length() > 9 && str.charAt(8) == '@';
    }
}
