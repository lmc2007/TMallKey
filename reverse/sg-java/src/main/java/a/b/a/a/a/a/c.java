package a.b.a.a.a.a;

import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.sdk.simulatordetect.ISimulatorDetectComponent;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class c implements ISimulatorDetectComponent {

    /* renamed from: a, reason: collision with root package name */
    private com.alibaba.wireless.security.open.simulatordetect.ISimulatorDetectComponent f3a;

    public c(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f3a = (com.alibaba.wireless.security.open.simulatordetect.ISimulatorDetectComponent) iSecurityGuardPlugin.getInterface(com.alibaba.wireless.security.open.simulatordetect.ISimulatorDetectComponent.class);
    }

    public boolean isSimulator() {
        return this.f3a.isSimulator();
    }
}
