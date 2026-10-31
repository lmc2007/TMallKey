package com.alibaba.wireless.security.middletierplugin.d.a;

import com.alibaba.wireless.security.middletierplugin.SecurityGuardMiddleTierPlugin;
import com.alibaba.wireless.security.open.avmp.IAVMPSoftCertComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class c implements IAVMPSoftCertComponent {
    private ISecurityGuardPlugin a;

    public c(SecurityGuardMiddleTierPlugin securityGuardMiddleTierPlugin) {
        init(securityGuardMiddleTierPlugin, new Object[0]);
    }

    public byte[] generateCSR(String str, String str2, int i) {
        return (byte[]) this.a.getRouter().doCommand(60202, new Object[]{str, str2, Integer.valueOf(i)});
    }

    public String getCert(String str) {
        return (String) this.a.getRouter().doCommand(60206, new Object[]{str});
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.a = iSecurityGuardPlugin;
        return 0;
    }

    public boolean initAVMPSoftCert(String str) {
        return ((Boolean) this.a.getRouter().doCommand(60201, new Object[]{str})).booleanValue();
    }

    public boolean installCert(String str, String str2) {
        return ((Boolean) this.a.getRouter().doCommand(60205, new Object[]{str, str2})).booleanValue();
    }

    public byte[] signWithCert(String str, byte[] bArr, int i) {
        return (byte[]) this.a.getRouter().doCommand(60203, new Object[]{str, bArr, Integer.valueOf(i)});
    }

    public boolean verifyWithCert(String str, byte[] bArr, byte[] bArr2, int i) {
        return ((Boolean) this.a.getRouter().doCommand(60204, new Object[]{str, bArr, bArr2, Integer.valueOf(i)})).booleanValue();
    }
}
