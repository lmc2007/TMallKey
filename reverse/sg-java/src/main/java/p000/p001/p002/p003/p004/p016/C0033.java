package p000.p001.p002.p003.p004.p016;

import android.util.Base64;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.statickeyencrypt.IStaticKeyEncryptComponent;
import com.taobao.wireless.security.adapter.common.SPUtility2;

/* renamed from: а.а.а.а.а.л.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0033 implements IStaticKeyEncryptComponent {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f95 = null;

    public C0033(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    /* renamed from: а, reason: contains not printable characters */
    private int m112(String str, byte[] bArr) throws SecException {
        if (str == null || str.length() <= 0 || bArr == null || bArr.length <= 0) {
            throw new SecException(701);
        }
        byte[] m117 = m117(704, str.getBytes(), bArr);
        if (m117 != null && m117.length > 0) {
            String readFromSPUnified = SPUtility2.readFromSPUnified("StaticKey", str, null);
            if (SPUtility2.saveToSPUnified("StaticKey", str, new String(m117), true)) {
                return (readFromSPUnified == null || readFromSPUnified.length() <= 0) ? 1 : 2;
            }
        }
        return 0;
    }

    /* renamed from: а, reason: contains not printable characters */
    private boolean m113(String str) {
        String readFromSPUnified = SPUtility2.readFromSPUnified("StaticKey", str, null);
        return readFromSPUnified != null && readFromSPUnified.length() > 0;
    }

    /* renamed from: а, reason: contains not printable characters */
    private byte[] m114(int i, int i2, byte[] bArr, byte[] bArr2) {
        return (byte[]) this.f95.getRouter().doCommand(10605, new Object[]{Integer.valueOf(i), Integer.valueOf(i2), bArr, bArr2});
    }

    /* renamed from: а, reason: contains not printable characters */
    private byte[] m115(int i, String str, String str2) throws SecException {
        String readFromSPUnified = SPUtility2.readFromSPUnified("StaticKey", str, null);
        String readFromSPUnified2 = SPUtility2.readFromSPUnified("StaticKey", str2, null);
        if (readFromSPUnified == null || readFromSPUnified.length() == 0) {
            throw new SecException(703);
        }
        if (readFromSPUnified2 == null || readFromSPUnified2.length() == 0) {
            throw new SecException(703);
        }
        byte[] m114 = m114(703, i, readFromSPUnified.getBytes(), readFromSPUnified2.getBytes());
        if (m114 == null || m114.length <= 0) {
            return m114;
        }
        try {
            return Base64.decode(m114, 0);
        } catch (Exception unused) {
            return null;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    private byte[] m116(int i, String str, byte[] bArr) throws SecException {
        String readFromSPUnified = SPUtility2.readFromSPUnified("StaticKey", str, null);
        if (readFromSPUnified == null || readFromSPUnified.length() <= 0) {
            throw new SecException(703);
        }
        return m114(702, i, readFromSPUnified.getBytes(), Base64.encode(bArr, 2));
    }

    /* renamed from: а, reason: contains not printable characters */
    private byte[] m117(int i, byte[] bArr, byte[] bArr2) {
        return (byte[]) this.f95.getRouter().doCommand(10605, new Object[]{Integer.valueOf(i), bArr, bArr2});
    }

    /* renamed from: б, reason: contains not printable characters */
    private int m118(String str) {
        if (SPUtility2.readFromSPUnified("StaticKey", str, null) == null) {
            return 2;
        }
        return SPUtility2.removeFromSPUnified("StaticKey", str, true) ? 1 : 0;
    }

    /* renamed from: б, reason: contains not printable characters */
    private byte[] m119(int i, String str, byte[] bArr) throws SecException {
        String readFromSPUnified = SPUtility2.readFromSPUnified("StaticKey", str, null);
        if (readFromSPUnified == null || readFromSPUnified.length() <= 0) {
            throw new SecException(703);
        }
        byte[] m114 = m114(701, i, readFromSPUnified.getBytes(), bArr);
        return (m114 == null || m114.length <= 0) ? m114 : Base64.decode(m114, 0);
    }

    public byte[] decrypt(int i, String str, byte[] bArr) throws SecException {
        if (str == null || str.length() <= 0 || bArr == null || bArr.length <= 0 || i < 16 || i > 18) {
            throw new SecException(701);
        }
        return m116(i, str, bArr);
    }

    public byte[] encrypt(int i, String str, byte[] bArr) throws SecException {
        if (str == null || str.length() <= 0 || bArr == null || bArr.length <= 0 || i < 16 || i > 18) {
            throw new SecException(701);
        }
        return m119(i, str, bArr);
    }

    public byte[] encryptSecretData(int i, String str, String str2) throws SecException {
        if (str == null || str.length() <= 0 || str2 == null || str2.length() <= 0 || i < 16 || i > 18) {
            throw new SecException(701);
        }
        return m115(i, str, str2);
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f95 = iSecurityGuardPlugin;
        return 0;
    }

    public boolean isSecretExist(String str) throws SecException {
        if (str == null || str.length() <= 0) {
            throw new SecException(701);
        }
        return m113(str);
    }

    public int removeSecret(String str) throws SecException {
        if (str == null || str.length() <= 0) {
            throw new SecException(701);
        }
        return m118(str);
    }

    public int saveSecret(String str, byte[] bArr) throws SecException {
        if (str == null || str.length() <= 0 || bArr == null || bArr.length <= 0) {
            throw new SecException(701);
        }
        return m112(str, bArr);
    }
}
