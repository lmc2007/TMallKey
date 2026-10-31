package p000.p001.p002.p003.p004.p017;

import com.alibaba.wireless.security.mainplugin.C0002;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.litevm.LiteVMInstance;
import com.alibaba.wireless.security.open.litevm.LiteVMParamType;
import com.alibaba.wireless.security.open.litevm.LiteVMParameterWrapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/* renamed from: а.а.а.а.а.ё.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0034 {

    /* renamed from: а, reason: contains not printable characters */
    private static ISecurityGuardPlugin f96;

    /* renamed from: б, reason: contains not printable characters */
    private static volatile Object f97;

    /* renamed from: в, reason: contains not printable characters */
    private static C0034 f98;

    /* renamed from: а.а.а.а.а.ё.а$а, reason: contains not printable characters */
    static class C0035 implements InvocationHandler {
        C0035() {
        }

        /* renamed from: а, reason: contains not printable characters */
        private boolean m133(Object obj, Object obj2) {
            if (obj2 != null && obj2.getClass() == obj.getClass()) {
                try {
                    return equals(Proxy.getInvocationHandler(obj2));
                } catch (Exception unused) {
                }
            }
            return false;
        }

        /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) throws Throwable {
            char c;
            String name = method.getName();
            switch (name.hashCode()) {
                case -1776922004:
                    if (name.equals("toString")) {
                        c = '\b';
                        break;
                    }
                    c = 65535;
                    break;
                case -1566653364:
                    if (name.equals("callLiteVMByteMethod")) {
                        c = 6;
                        break;
                    }
                    c = 65535;
                    break;
                case -1376216685:
                    if (name.equals("reloadLiteVMInstance")) {
                        c = 2;
                        break;
                    }
                    c = 65535;
                    break;
                case -1295482945:
                    if (name.equals("equals")) {
                        c = '\t';
                        break;
                    }
                    c = 65535;
                    break;
                case -611610698:
                    if (name.equals("createLiteVMInstance")) {
                        c = 0;
                        break;
                    }
                    c = 65535;
                    break;
                case -514295244:
                    if (name.equals("destroyLiteVMInstance")) {
                        c = 3;
                        break;
                    }
                    c = 65535;
                    break;
                case -320658273:
                    if (name.equals("createLiteVMInstanceWithSymbols")) {
                        c = 1;
                        break;
                    }
                    c = 65535;
                    break;
                case 66098133:
                    if (name.equals("callLiteVMStringMethod")) {
                        c = 5;
                        break;
                    }
                    c = 65535;
                    break;
                case 147696667:
                    if (name.equals("hashCode")) {
                        c = '\n';
                        break;
                    }
                    c = 65535;
                    break;
                case 1109138392:
                    if (name.equals("callLiteVMVoidMethod")) {
                        c = 7;
                        break;
                    }
                    c = 65535;
                    break;
                case 1565934272:
                    if (name.equals("callLiteVMLongMethod")) {
                        c = 4;
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
                    return C0034.f98.m121((String) objArr[0], (String) objArr[1], (byte[]) objArr[2], (Object[]) objArr[3]);
                case 1:
                    return C0034.f98.m120((String) objArr[0], (String) objArr[1], (byte[]) objArr[2], ((Long) objArr[3]).longValue());
                case 2:
                    C0034.f98.m129((LiteVMInstance) objArr[0], (byte[]) objArr[1]);
                    return null;
                case 3:
                    C0034.f98.m128((LiteVMInstance) objArr[0]);
                    return null;
                case 4:
                    return C0034.f98.m124((LiteVMInstance) objArr[0], ((Integer) objArr[1]).intValue(), (LiteVMParameterWrapper[]) objArr[2], 1);
                case 5:
                    return C0034.f98.m124((LiteVMInstance) objArr[0], ((Integer) objArr[1]).intValue(), (LiteVMParameterWrapper[]) objArr[2], 2);
                case 6:
                    return C0034.f98.m124((LiteVMInstance) objArr[0], ((Integer) objArr[1]).intValue(), (LiteVMParameterWrapper[]) objArr[2], 3);
                case 7:
                    C0034.f98.m124((LiteVMInstance) objArr[0], ((Integer) objArr[1]).intValue(), (LiteVMParameterWrapper[]) objArr[2], 0);
                    return null;
                case '\b':
                    return obj.getClass().getName() + "&ID=" + hashCode();
                case '\t':
                    return Boolean.valueOf(m133(obj, objArr[0]));
                case '\n':
                    return Integer.valueOf(hashCode());
                default:
                    return null;
            }
        }
    }

    private C0034(ISecurityGuardPlugin iSecurityGuardPlugin) throws ClassNotFoundException {
        f96 = iSecurityGuardPlugin;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: а, reason: contains not printable characters */
    public LiteVMInstance m120(String str, String str2, byte[] bArr, long j) throws SecException {
        return new LiteVMInstance(C0036.m134(f96, str, str2, bArr, j), str, str2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: а, reason: contains not printable characters */
    public LiteVMInstance m121(String str, String str2, byte[] bArr, Object[] objArr) throws SecException {
        return new LiteVMInstance(C0036.m135(f96, str, str2, bArr, (Object[]) null), str, str2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: а, reason: contains not printable characters */
    public Object m124(LiteVMInstance liteVMInstance, int i, LiteVMParameterWrapper[] liteVMParameterWrapperArr, int i2) throws SecException {
        if (liteVMInstance == null || !m132(liteVMInstance)) {
            throw new SecException("LVM instance not valid", 2101);
        }
        LiteVMParameterWrapper[] liteVMParameterWrapperArr2 = null;
        if (liteVMParameterWrapperArr != null) {
            liteVMParameterWrapperArr2 = (LiteVMParameterWrapper[]) liteVMParameterWrapperArr.clone();
            int length = liteVMParameterWrapperArr2.length;
            for (int i3 = 0; i3 < length; i3++) {
                LiteVMParameterWrapper liteVMParameterWrapper = liteVMParameterWrapperArr2[i3];
                if (liteVMParameterWrapper != null && liteVMParameterWrapper.getType() == LiteVMParamType.PARAM_TYPE.PARAM_TYPE_STRING.getValue() && liteVMParameterWrapper.getValue() != null) {
                    try {
                        byte[] bytes = ((String) liteVMParameterWrapper.getValue()).getBytes("UTF-8");
                        byte[] bArr = new byte[bytes.length + 1];
                        for (int i4 = 0; i4 < bytes.length; i4++) {
                            bArr[i4] = bytes[i4];
                        }
                        bArr[bytes.length] = 0;
                        liteVMParameterWrapperArr2[i3] = new LiteVMParameterWrapper(LiteVMParamType.PARAM_TYPE.PARAM_TYPE_STRING, bArr);
                    } catch (Exception unused) {
                        throw new SecException("LVM string param convert error", 2101);
                    }
                }
            }
        }
        return ((C0036) liteVMInstance.getImpl()).m137(i, liteVMParameterWrapperArr2, i2, 12504);
    }

    /* renamed from: а, reason: contains not printable characters */
    public static Object m125(Class cls, ISecurityGuardPlugin iSecurityGuardPlugin) throws ClassNotFoundException {
        Class.forName("com.alibaba.wireless.security.open.litevm.LiteVMInstance");
        if (f97 == null) {
            synchronized (C0034.class) {
                if (f97 == null && iSecurityGuardPlugin != null) {
                    f98 = new C0034(iSecurityGuardPlugin);
                    f97 = C0002.m5(cls, new C0035());
                }
            }
        }
        return f97;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: а, reason: contains not printable characters */
    public void m128(LiteVMInstance liteVMInstance) throws SecException {
        if (liteVMInstance == null || !m132(liteVMInstance)) {
            throw new SecException("LVM instance not valid", 2101);
        }
        ((C0036) liteVMInstance.getImpl()).m138();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: а, reason: contains not printable characters */
    public void m129(LiteVMInstance liteVMInstance, byte[] bArr) throws SecException {
        if (liteVMInstance == null || !m132(liteVMInstance)) {
            throw new SecException("lvm instance not valid", 2101);
        }
        ((C0036) liteVMInstance.getImpl()).m139(bArr);
    }

    /* renamed from: б, reason: contains not printable characters */
    private boolean m132(LiteVMInstance liteVMInstance) {
        Object impl;
        if (liteVMInstance == null || (impl = liteVMInstance.getImpl()) == null || !(impl instanceof C0036)) {
            return false;
        }
        return ((C0036) impl).m140();
    }
}
