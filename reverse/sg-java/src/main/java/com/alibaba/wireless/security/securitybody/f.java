package com.alibaba.wireless.security.securitybody;

import com.alibaba.wireless.security.open.maldetection.IMalDetect;
import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;
import com.alibaba.wireless.security.open.simulatordetect.ISimulatorDetectComponent;
import com.alibaba.wireless.security.open.umid.IUMIDComponent;
import com.alibaba.wireless.security.open.umid.UMIDComponent;
import com.alibaba.wireless.security.securitybody.open.MalDetect;
import com.taobao.wireless.security.sdk.rootdetect.IRootDetectComponent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class f {
    public static Object a(Class cls, InvocationHandler invocationHandler) {
        return Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, invocationHandler);
    }

    public void a(HashMap<Class, Object> hashMap, SecurityGuardSecurityBodyPlugin securityGuardSecurityBodyPlugin) {
        hashMap.put(IUMIDComponent.class, new UMIDComponent(securityGuardSecurityBodyPlugin));
        hashMap.put(ISimulatorDetectComponent.class, new com.alibaba.wireless.security.securitybody.open.d(securityGuardSecurityBodyPlugin));
        hashMap.put(IMalDetect.class, new MalDetect(securityGuardSecurityBodyPlugin));
        hashMap.put(ISecurityBodyComponent.class, new com.alibaba.wireless.security.securitybody.open.b(securityGuardSecurityBodyPlugin));
        try {
            Class<?> cls = Class.forName("com.alibaba.wireless.security.open.securitybodysdk.ISecurityBodyPageTrack");
            if (cls != null) {
                hashMap.put(cls, com.alibaba.wireless.security.securitybody.open.c.a(cls, securityGuardSecurityBodyPlugin));
            }
            Class<?> cls2 = Class.forName("com.alibaba.wireless.security.open.lbsrisk.ILBSRiskComponent");
            if (cls2 != null) {
                hashMap.put(cls2, com.alibaba.wireless.security.securitybody.open.a.a(cls2, securityGuardSecurityBodyPlugin));
            }
        } catch (Exception unused) {
        }
    }
}
