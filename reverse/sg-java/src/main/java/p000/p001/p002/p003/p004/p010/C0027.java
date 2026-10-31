package p000.p001.p002.p003.p004.p010;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.dynamicdatastore.IDynamicDataStoreComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.io.UnsupportedEncodingException;
import p000.p001.p002.p003.p004.p006.C0023;

/* renamed from: а.а.а.а.а.е.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0027 implements IDynamicDataStoreComponent {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f89;

    public C0027(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    /* renamed from: а, reason: contains not printable characters */
    private int m102(String str, String str2, String str3, boolean z) throws SecException {
        return ((Integer) this.f89.getRouter().doCommand(10502, new Object[]{5, str3, Boolean.valueOf(z), str, str2})).intValue();
    }

    /* renamed from: а, reason: contains not printable characters */
    private Object m103(int i, String str, byte[] bArr, int i2) throws SecException {
        if (str == null || str.length() <= 0) {
            throw new SecException("", 501);
        }
        if (i == 8 || i == 9 || i == 13 || i == 12) {
            return (Boolean) this.f89.getRouter().doCommand(10503, new Object[]{Integer.valueOf(i), str, bArr, Integer.valueOf(i2)});
        }
        if (i != 10 && i != 11) {
            return null;
        }
        byte[] bArr2 = (byte[]) this.f89.getRouter().doCommand(10503, new Object[]{Integer.valueOf(i), str, bArr, Integer.valueOf(i2)});
        return i == 10 ? new String(bArr2) : bArr2;
    }

    /* renamed from: а, reason: contains not printable characters */
    private String m104(String str, String str2, boolean z) throws SecException {
        byte[] bArr = (byte[]) this.f89.getRouter().doCommand(10502, new Object[]{6, str2, Boolean.valueOf(z), str, null});
        if (bArr != null) {
            try {
                if (bArr.length > 0) {
                    return new String(bArr, "UTF-8");
                }
            } catch (Exception unused) {
                throw new SecException("return invalid String result", 499);
            }
        }
        throw new SecException("return null byteResult", 499);
    }

    /* renamed from: б, reason: contains not printable characters */
    private void m105(String str, String str2, boolean z) throws SecException {
        this.f89.getRouter().doCommand(10502, new Object[]{7, str2, Boolean.valueOf(z), str, null});
    }

    public boolean getBoolean(String str) throws SecException {
        String m104 = m104(str, "Z", false);
        if (m104 == null) {
            return false;
        }
        try {
            return "1".equals(m104);
        } catch (Throwable unused) {
            return false;
        }
    }

    public byte[] getByteArray(String str) throws SecException {
        String m104 = m104(str, "[B", false);
        if (m104 != null) {
            try {
                return C0023.m98(m104);
            } catch (Throwable unused) {
            }
        }
        return null;
    }

    public byte[] getByteArrayDDp(String str) throws SecException {
        String m104 = m104(str, "[B", true);
        if (m104 != null) {
            try {
                return C0023.m98(m104);
            } catch (Throwable unused) {
            }
        }
        return null;
    }

    public byte[] getByteArrayDDpEx(String str, int i) throws SecException {
        return (byte[]) m103(11, str, (byte[]) null, i);
    }

    public float getFloat(String str) throws SecException {
        String m104 = m104(str, "F", false);
        if (m104 != null) {
            try {
                return Float.parseFloat(m104);
            } catch (Throwable unused) {
            }
        }
        return -1.0f;
    }

    public int getInt(String str) throws SecException {
        String m104 = m104(str, "I", false);
        if (m104 != null) {
            try {
                return Integer.parseInt(m104);
            } catch (Throwable unused) {
            }
        }
        return -1;
    }

    public long getLong(String str) throws SecException {
        String m104 = m104(str, "J", false);
        if (m104 != null) {
            try {
                return Long.parseLong(m104);
            } catch (Throwable unused) {
            }
        }
        return -1L;
    }

    public String getString(String str) throws SecException {
        return m104(str, "LString", false);
    }

    public String getStringDDp(String str) throws SecException {
        return m104(str, "LString", true);
    }

    public String getStringDDpEx(String str, int i) throws SecException {
        return (String) m103(10, str, (byte[]) null, i);
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f89 = iSecurityGuardPlugin;
        return 0;
    }

    public int putBoolean(String str, boolean z) throws SecException {
        return m102(str, z ? "1" : "0", "Z", false);
    }

    public int putByteArray(String str, byte[] bArr) throws SecException {
        return m102(str, C0023.m97(bArr), "[B", false);
    }

    public int putByteArrayDDp(String str, byte[] bArr) throws SecException {
        return m102(str, C0023.m97(bArr), "[B", true);
    }

    public boolean putByteArrayDDpEx(String str, byte[] bArr, int i) throws SecException {
        return ((Boolean) m103(9, str, bArr, i)).booleanValue();
    }

    public int putFloat(String str, float f) throws SecException {
        return m102(str, Float.toString(f), "F", false);
    }

    public int putInt(String str, int i) throws SecException {
        return m102(str, Integer.toString(i), "I", false);
    }

    public int putLong(String str, long j) throws SecException {
        return m102(str, Long.toString(j), "J", false);
    }

    public int putString(String str, String str2) throws SecException {
        return m102(str, str2, "LString", false);
    }

    public int putStringDDp(String str, String str2) throws SecException {
        return m102(str, str2, "LString", true);
    }

    public boolean putStringDDpEx(String str, String str2, int i) throws SecException {
        if (str2 == null || str2.length() <= 0) {
            throw new SecException("", 501);
        }
        try {
            return ((Boolean) m103(8, str, str2.getBytes("UTF-8"), i)).booleanValue();
        } catch (UnsupportedEncodingException unused) {
            throw new SecException("", 501);
        }
    }

    public void removeBoolean(String str) throws SecException {
        m105(str, "Z", false);
    }

    public void removeByteArray(String str) throws SecException {
        m105(str, "[B", false);
    }

    public void removeByteArrayDDp(String str) throws SecException {
        m105(str, "[B", true);
    }

    public void removeByteArrayDDpEx(String str, int i) throws SecException {
        m103(13, str, (byte[]) null, i);
    }

    public void removeFloat(String str) throws SecException {
        m105(str, "F", false);
    }

    public void removeInt(String str) throws SecException {
        m105(str, "I", false);
    }

    public void removeLong(String str) throws SecException {
        m105(str, "J", false);
    }

    public void removeString(String str) throws SecException {
        m105(str, "LString", false);
    }

    public void removeStringDDp(String str) throws SecException {
        m105(str, "LString", true);
    }

    public void removeStringDDpEx(String str, int i) throws SecException {
        m103(12, str, (byte[]) null, i);
    }
}
