package com.alibaba.wireless.security.middletierplugin.d.a;

import com.alibaba.wireless.security.middletierplugin.SecurityGuardMiddleTierPlugin;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.avmp.IAVMPSafeTokenComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import java.io.UnsupportedEncodingException;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class b implements IAVMPSafeTokenComponent {
    private ISecurityGuardPlugin a;

    public b(SecurityGuardMiddleTierPlugin securityGuardMiddleTierPlugin) {
        init(securityGuardMiddleTierPlugin, new Object[0]);
    }

    private Object a(int i, String str, byte[] bArr, String str2, int i2) {
        if (i == 0) {
            return (Boolean) this.a.getRouter().doCommand(60401, new Object[]{Integer.valueOf(i), str, bArr, str2, Integer.valueOf(i2)});
        }
        if (str == null || str.length() <= 0) {
            throw new SecException("", 1601);
        }
        if (i > 0 && i < 4) {
            return (Boolean) this.a.getRouter().doCommand(60401, new Object[]{Integer.valueOf(i), str, bArr, str2, Integer.valueOf(i2)});
        }
        if (i <= 3 || i > 6) {
            return null;
        }
        byte[] bArr2 = (byte[]) this.a.getRouter().doCommand(60401, new Object[]{Integer.valueOf(i), str, bArr, str2, Integer.valueOf(i2)});
        return i == 6 ? new String(bArr2) : bArr2;
    }

    public byte[] decryptWithToken(String str, byte[] bArr, int i) {
        return (byte[]) a(5, str, bArr, null, i);
    }

    public byte[] encryptWithToken(String str, byte[] bArr, int i) {
        return (byte[]) a(4, str, bArr, null, i);
    }

    public int getOTP(String str, int i) {
        return ((Integer) this.a.getRouter().doCommand(60402, new Object[]{str, Integer.valueOf(i)})).intValue();
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.a = iSecurityGuardPlugin;
        return 0;
    }

    public boolean initAVMPSafeToken(String str) {
        return ((Boolean) a(0, null, null, str, 0)).booleanValue();
    }

    public boolean isTokenExisted(String str) {
        return ((Boolean) a(2, str, null, null, 0)).booleanValue();
    }

    public boolean removeToken(String str) {
        return ((Boolean) a(3, str, null, null, 0)).booleanValue();
    }

    public boolean saveToken(String str, String str2, String str3, int i) {
        if (str2 == null || str2.length() <= 0) {
            throw new SecException("", 1601);
        }
        try {
            return ((Boolean) a(1, str, str2.getBytes("UTF-8"), str3, i)).booleanValue();
        } catch (UnsupportedEncodingException unused) {
            throw new SecException("", 1601);
        }
    }

    public String signWithToken(String str, byte[] bArr, int i) {
        return (String) a(6, str, bArr, null, i);
    }
}
