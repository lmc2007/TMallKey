package com.alibaba.wireless.security.middletierplugin.d.a;

import com.alibaba.wireless.security.middletierplugin.SecurityGuardMiddleTierPlugin;
import com.alibaba.wireless.security.open.avmp.IAVMPGenericComponent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmiddletier\classes.dex */
public class a implements IAVMPGenericComponent {
    private ISecurityGuardPlugin a = null;

    /* renamed from: com.alibaba.wireless.security.middletierplugin.d.a.a$a, reason: collision with other inner class name */
    public class C0002a implements IAVMPGenericComponent.IAVMPGenericInstance {
        private ISecurityGuardPlugin a;
        private long b = -1;

        public C0002a(a aVar, ISecurityGuardPlugin iSecurityGuardPlugin) {
            this.a = null;
            this.a = iSecurityGuardPlugin;
        }

        void a(String str, String str2) {
            this.b = ((Long) this.a.getRouter().doCommand(70201, new Object[]{str, str2})).longValue();
        }

        boolean a() {
            return ((Boolean) this.a.getRouter().doCommand(70203, new Object[]{Long.valueOf(this.b)})).booleanValue();
        }

        public Object invokeAVMP(String str, Class cls, Object... objArr) {
            return this.a.getRouter().doCommand(70202, new Object[]{Long.valueOf(this.b), str, cls, objArr});
        }

        public Object invokeAVMP2(String str, Class cls, Object... objArr) {
            return this.a.getRouter().doCommand(70204, new Object[]{Long.valueOf(this.b), str, cls, objArr});
        }
    }

    public a(SecurityGuardMiddleTierPlugin securityGuardMiddleTierPlugin) {
        init(securityGuardMiddleTierPlugin, new Object[0]);
    }

    public IAVMPGenericComponent.IAVMPGenericInstance createAVMPInstance(String str, String str2) {
        C0002a c0002a = new C0002a(this, this.a);
        c0002a.a(str, str2);
        return c0002a;
    }

    public boolean destroyAVMPInstance(IAVMPGenericComponent.IAVMPGenericInstance iAVMPGenericInstance) {
        return ((C0002a) iAVMPGenericInstance).a();
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.a = iSecurityGuardPlugin;
        return 0;
    }
}
