package p000.p001.p002.p003.p004.p013;

import com.alibaba.wireless.security.mainplugin.SecurityGuardMainPlugin;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.SecurityGuardParamContext;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent;
import com.alibaba.wireless.security.open.staticdataencrypt.IStaticDataEncryptComponent;
import java.util.HashMap;
import java.util.Map;

/* renamed from: а.а.а.а.а.и.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0030 implements ISecureSignatureComponent {

    /* renamed from: а, reason: contains not printable characters */
    private ISecurityGuardPlugin f92 = null;

    public C0030(SecurityGuardMainPlugin securityGuardMainPlugin) {
        init(securityGuardMainPlugin, new Object[0]);
    }

    public String getSafeCookie(String str, String str2, String str3) throws SecException {
        try {
            ISecureSignatureComponent iSecureSignatureComponent = (ISecureSignatureComponent) this.f92.getInterface(ISecureSignatureComponent.class);
            IStaticDataEncryptComponent iStaticDataEncryptComponent = (IStaticDataEncryptComponent) this.f92.getInterface(IStaticDataEncryptComponent.class);
            HashMap hashMap = new HashMap();
            hashMap.put("INPUT", str);
            SecurityGuardParamContext securityGuardParamContext = new SecurityGuardParamContext();
            securityGuardParamContext.appKey = str2;
            securityGuardParamContext.paramMap = hashMap;
            securityGuardParamContext.requestType = 3;
            return "AE001" + iStaticDataEncryptComponent.staticSafeEncrypt(16, str2, iSecureSignatureComponent.signRequest(securityGuardParamContext, str3) + str, str3);
        } catch (SecException e) {
            int i = 699;
            if (e.getErrorCode() > 600 && e.getErrorCode() < 699) {
                throw e;
            }
            switch (e.getErrorCode()) {
                case 302:
                    i = 607;
                    break;
                case 303:
                    i = 608;
                    break;
                case 304:
                    i = 304;
                    break;
                case 305:
                    i = 305;
                    break;
                case 306:
                    i = 306;
                    break;
                case 307:
                    i = 307;
                    break;
                case 308:
                    i = 308;
                    break;
            }
            throw new SecException(i);
        }
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f92 = iSecurityGuardPlugin;
        return 0;
    }

    public String signRequest(SecurityGuardParamContext securityGuardParamContext, String str) throws SecException {
        Map map;
        if (securityGuardParamContext == null || (map = securityGuardParamContext.paramMap) == null) {
            throw new SecException("", 601);
        }
        String str2 = securityGuardParamContext.appKey;
        int i = securityGuardParamContext.requestType;
        if (map == null || map.isEmpty()) {
            throw new SecException("", 601);
        }
        if (i == 0) {
            if (((String) map.get("SEEDKEY")) == null) {
                throw new SecException(606);
            }
        } else if (i == 5 || i == 7 || i == 8) {
            String str3 = (String) map.get("ATLAS");
            if (map.size() == 2 && (str3 == null || str3.length() <= 0)) {
                throw new SecException(601);
            }
        }
        String str4 = (String) this.f92.getRouter().doCommand(10401, new Object[]{map, str2, Integer.valueOf(i), str, true});
        return ((i == 0 || i == 2) && str4 != null) ? str4.toUpperCase() : str4;
    }
}
