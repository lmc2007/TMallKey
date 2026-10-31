package com.alibaba.wireless.security.mainplugin;

import com.alibaba.wireless.security.open.atlasencrypt.IAtlasEncryptComponent;
import com.alibaba.wireless.security.open.compat.ICompatComponent;
import com.alibaba.wireless.security.open.datacollection.IDataCollectionComponent;
import com.alibaba.wireless.security.open.dynamicdataencrypt.IDynamicDataEncryptComponent;
import com.alibaba.wireless.security.open.dynamicdatastore.IDynamicDataStoreComponent;
import com.alibaba.wireless.security.open.opensdk.IOpenSDKComponent;
import com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent;
import com.alibaba.wireless.security.open.staticdataencrypt.IStaticDataEncryptComponent;
import com.alibaba.wireless.security.open.staticdatastore.IStaticDataStoreComponent;
import com.alibaba.wireless.security.open.statickeyencrypt.IStaticKeyEncryptComponent;
import com.taobao.wireless.security.sdk.safetoken.ISafeTokenComponent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import p000.p001.p002.p003.p004.p005.C0022;
import p000.p001.p002.p003.p004.p007.C0024;
import p000.p001.p002.p003.p004.p008.C0025;
import p000.p001.p002.p003.p004.p009.C0026;
import p000.p001.p002.p003.p004.p010.C0027;
import p000.p001.p002.p003.p004.p011.C0028;
import p000.p001.p002.p003.p004.p012.C0029;
import p000.p001.p002.p003.p004.p013.C0030;
import p000.p001.p002.p003.p004.p014.C0031;
import p000.p001.p002.p003.p004.p015.C0032;
import p000.p001.p002.p003.p004.p016.C0033;
import p000.p001.p002.p003.p004.p017.C0034;
import p000.p018.p019.p020.p021.p022.C0037;
import p000.p018.p019.p020.p021.p023.C0038;
import p000.p018.p019.p020.p021.p024.C0039;
import p000.p018.p019.p020.p021.p025.C0040;
import p000.p018.p019.p020.p021.p026.C0041;
import p000.p018.p019.p020.p021.p027.C0042;
import p000.p018.p019.p020.p021.p028.C0043;
import p000.p018.p019.p020.p021.p029.C0044;

/* renamed from: com.alibaba.wireless.security.mainplugin.в, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0002 {
    /* renamed from: а, reason: contains not printable characters */
    public static Object m5(Class cls, InvocationHandler invocationHandler) {
        return Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, invocationHandler);
    }

    /* renamed from: а, reason: contains not printable characters */
    public void m6(HashMap<Class, Object> hashMap, SecurityGuardMainPlugin securityGuardMainPlugin) {
        hashMap.put(IDataCollectionComponent.class, new C0025(securityGuardMainPlugin));
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* renamed from: а, reason: contains not printable characters */
    public void m7(HashMap<Class, Object> hashMap, String str, SecurityGuardMainPlugin securityGuardMainPlugin) {
        char c;
        Class<ISafeTokenComponent> cls;
        Object c0022;
        Object m125;
        switch (str.hashCode()) {
            case -2107806319:
                if (str.equals("com.alibaba.wireless.security.open.staticdataencrypt.IStaticDataEncryptComponent")) {
                    c = 6;
                    break;
                }
                c = 65535;
                break;
            case -1726912853:
                if (str.equals("com.alibaba.wireless.security.open.dynamicdatastore.IDynamicDataStoreComponent")) {
                    c = 3;
                    break;
                }
                c = 65535;
                break;
            case -1508190505:
                if (str.equals("com.alibaba.wireless.security.open.compat.ICompatComponent")) {
                    c = 11;
                    break;
                }
                c = 65535;
                break;
            case -1062752269:
                if (str.equals("com.taobao.wireless.security.sdk.safetoken.ISafeTokenComponent")) {
                    c = 19;
                    break;
                }
                c = 65535;
                break;
            case -759346967:
                if (str.equals("com.taobao.wireless.security.sdk.atlasencrypt.IAtlasEncryptComponent")) {
                    c = '\f';
                    break;
                }
                c = 65535;
                break;
            case -680196717:
                if (str.equals("com.taobao.wireless.security.sdk.securesignature.ISecureSignatureComponent")) {
                    c = 16;
                    break;
                }
                c = 65535;
                break;
            case -319873839:
                if (str.equals("com.alibaba.wireless.security.open.safetoken.ISafeTokenComponent")) {
                    c = '\t';
                    break;
                }
                c = 65535;
                break;
            case -308669071:
                if (str.equals("com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent")) {
                    c = 5;
                    break;
                }
                c = 65535;
                break;
            case -202074525:
                if (str.equals("com.alibaba.wireless.security.open.dynamicdataencrypt.IDynamicDataEncryptComponent")) {
                    c = 2;
                    break;
                }
                c = 65535;
                break;
            case 541243213:
                if (str.equals("com.taobao.wireless.security.sdk.datacollection.IDataCollectionComponent")) {
                    c = '\r';
                    break;
                }
                c = 65535;
                break;
            case 656374963:
                if (str.equals("com.taobao.wireless.security.sdk.staticdataencrypt.IStaticDataEncryptComponent")) {
                    c = 17;
                    break;
                }
                c = 65535;
                break;
            case 1093102771:
                if (str.equals("com.taobao.wireless.security.sdk.staticdatastore.IStaticDataStoreComponent")) {
                    c = 18;
                    break;
                }
                c = 65535;
                break;
            case 1282550417:
                if (str.equals("com.alibaba.wireless.security.open.opensdk.IOpenSDKComponent")) {
                    c = 4;
                    break;
                }
                c = 65535;
                break;
            case 1441811571:
                if (str.equals("com.alibaba.wireless.security.open.statickeyencrypt.IStaticKeyEncryptComponent")) {
                    c = '\b';
                    break;
                }
                c = 65535;
                break;
            case 1464630417:
                if (str.equals("com.alibaba.wireless.security.open.staticdatastore.IStaticDataStoreComponent")) {
                    c = 7;
                    break;
                }
                c = 65535;
                break;
            case 1852512071:
                if (str.equals("com.alibaba.wireless.security.open.atlasencrypt.IAtlasEncryptComponent")) {
                    c = 0;
                    break;
                }
                c = 65535;
                break;
            case 1868651473:
                if (str.equals("com.alibaba.wireless.security.open.litevm.ILiteVMComponent")) {
                    c = '\n';
                    break;
                }
                c = 65535;
                break;
            case 1886348549:
                if (str.equals("com.taobao.wireless.security.sdk.dynamicdataencrypt.IDynamicDataEncryptComponent")) {
                    c = 14;
                    break;
                }
                c = 65535;
                break;
            case 2012272205:
                if (str.equals("com.taobao.wireless.security.sdk.dynamicdatastore.IDynamicDataStoreComponent")) {
                    c = 15;
                    break;
                }
                c = 65535;
                break;
            case 2038834859:
                if (str.equals("com.alibaba.wireless.security.open.datacollection.IDataCollectionComponent")) {
                    c = 1;
                    break;
                }
                c = 65535;
                break;
            default:
                c = 65535;
                break;
        }
        switch (c) {
            case 0:
                cls = IAtlasEncryptComponent.class;
                c0022 = new C0022(securityGuardMainPlugin);
                break;
            case 1:
                cls = IDataCollectionComponent.class;
                c0022 = new C0025(securityGuardMainPlugin);
                break;
            case 2:
                cls = IDynamicDataEncryptComponent.class;
                c0022 = new C0026(securityGuardMainPlugin);
                break;
            case 3:
                cls = IDynamicDataStoreComponent.class;
                c0022 = new C0027(securityGuardMainPlugin);
                break;
            case 4:
                cls = IOpenSDKComponent.class;
                c0022 = new C0028(securityGuardMainPlugin);
                break;
            case 5:
                cls = ISecureSignatureComponent.class;
                c0022 = new C0030(securityGuardMainPlugin);
                break;
            case 6:
                cls = IStaticDataEncryptComponent.class;
                c0022 = new C0031(securityGuardMainPlugin);
                break;
            case 7:
                cls = IStaticDataStoreComponent.class;
                c0022 = new C0032(securityGuardMainPlugin);
                break;
            case '\b':
                cls = IStaticKeyEncryptComponent.class;
                c0022 = new C0033(securityGuardMainPlugin);
                break;
            case '\t':
                cls = com.alibaba.wireless.security.open.safetoken.ISafeTokenComponent.class;
                c0022 = new C0029(securityGuardMainPlugin);
                break;
            case '\n':
                try {
                    Class<?> cls2 = Class.forName("com.alibaba.wireless.security.open.litevm.ILiteVMComponent");
                    if (cls2 == null || (m125 = C0034.m125(cls2, securityGuardMainPlugin)) == null) {
                        return;
                    }
                    hashMap.put(cls2, m125);
                    return;
                } catch (Exception unused) {
                    return;
                }
            case 11:
                cls = ICompatComponent.class;
                c0022 = new C0024(securityGuardMainPlugin);
                break;
            case '\f':
                cls = com.taobao.wireless.security.sdk.atlasencrypt.IAtlasEncryptComponent.class;
                c0022 = new C0037(securityGuardMainPlugin);
                break;
            case '\r':
                cls = com.taobao.wireless.security.sdk.datacollection.IDataCollectionComponent.class;
                c0022 = new C0038(securityGuardMainPlugin);
                break;
            case 14:
                cls = com.taobao.wireless.security.sdk.dynamicdataencrypt.IDynamicDataEncryptComponent.class;
                c0022 = new C0039(securityGuardMainPlugin);
                break;
            case 15:
                cls = com.taobao.wireless.security.sdk.dynamicdatastore.IDynamicDataStoreComponent.class;
                c0022 = new C0040(securityGuardMainPlugin);
                break;
            case 16:
                cls = com.taobao.wireless.security.sdk.securesignature.ISecureSignatureComponent.class;
                c0022 = new C0042(securityGuardMainPlugin);
                break;
            case 17:
                cls = com.taobao.wireless.security.sdk.staticdataencrypt.IStaticDataEncryptComponent.class;
                c0022 = new C0044(securityGuardMainPlugin);
                break;
            case 18:
                cls = com.taobao.wireless.security.sdk.staticdatastore.IStaticDataStoreComponent.class;
                c0022 = new C0043(securityGuardMainPlugin);
                break;
            case 19:
                cls = ISafeTokenComponent.class;
                c0022 = new C0041(securityGuardMainPlugin);
                break;
            default:
                return;
        }
        hashMap.put(cls, c0022);
    }
}
