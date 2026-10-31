package com.alibaba.minilibc.android;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.Map;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class MtopMethodJniBridge {
    private static final boolean DEBUG = false;
    private static final String TAG = "MtopMethodJniBridge";
    private static Class gEnvModeEnumClass = null;
    private static Class gMethodEnumClass = null;
    private static volatile int gMtopAvaiableCache = 0;
    private static volatile int gMtopAvaiableCacheChecked = 0;
    private static Method gMtopBuildMethod = null;
    private static Class gMtopBuilderClass = null;
    private static Method gMtopBuilderHeadersMethod = null;
    private static Method gMtopBuilderReqMethodMethod = null;
    private static Method gMtopBuilderSetConnectionTimeoutMilliSecondMethod = null;
    private static Method gMtopBuilderSetCustomDomainMethod = null;
    private static Method gMtopBuilderSetSocketTimeoutMilliSecondMethod = null;
    private static Method gMtopBuilderSyncRequestMethod = null;
    private static Method gMtopBusinessBuildMethod = null;
    private static Class gMtopClass = null;
    private static Method gMtopInstanceMethod = null;
    private static Class gMtopRequestClass = null;
    private static Method gMtopRequestSetApiNameMethod = null;
    private static Method gMtopRequestSetApiVersionMethod = null;
    private static Method gMtopRequestSetDataMethod = null;
    private static Class gMtopResponseClass = null;
    private static Method gMtopResponseGetDataJsonObjectMethod = null;
    private static Method gMtopResponseGetRetCodeMethod = null;
    private static Method gMtopResponseGetRetMsgMethod = null;
    private static Method gMtopResponseIsApiSuccessMethod = null;
    private static Class gReflectUtilClass = null;
    private static Method gReflectUtilConvertMapToDataStrMethod = null;
    private static boolean hasMtopBusiness = true;

    public static int mtopAvaiable() {
        if (gMtopAvaiableCacheChecked == 0) {
            synchronized (MtopMethodJniBridge.class) {
                if (gMtopAvaiableCacheChecked == 0) {
                    try {
                        gMtopBuilderClass = Class.forName("com.taobao.tao.remotebusiness.MtopBusiness");
                    } catch (ClassNotFoundException unused) {
                        hasMtopBusiness = DEBUG;
                    }
                    try {
                        gMtopClass = Class.forName("mtopsdk.mtop.intf.Mtop");
                        gMethodEnumClass = Class.forName("mtopsdk.mtop.domain.MethodEnum");
                        gEnvModeEnumClass = Class.forName("mtopsdk.mtop.domain.EnvModeEnum");
                        gMtopRequestClass = Class.forName("mtopsdk.mtop.domain.MtopRequest");
                        gMtopResponseClass = Class.forName("mtopsdk.mtop.domain.MtopResponse");
                        if (!hasMtopBusiness) {
                            gMtopBuilderClass = Class.forName("mtopsdk.mtop.intf.MtopBuilder");
                        }
                        gReflectUtilClass = Class.forName("mtopsdk.mtop.util.ReflectUtil");
                        gMtopRequestSetApiNameMethod = gMtopRequestClass.getMethod("setApiName", String.class);
                        gMtopRequestSetApiVersionMethod = gMtopRequestClass.getMethod("setVersion", String.class);
                        gMtopRequestSetDataMethod = gMtopRequestClass.getMethod("setData", String.class);
                        gMtopInstanceMethod = gMtopClass.getMethod("instance", Context.class);
                        gMtopBuildMethod = gMtopClass.getMethod("build", gMtopRequestClass, String.class);
                        gMtopBuilderReqMethodMethod = gMtopBuilderClass.getMethod("reqMethod", gMethodEnumClass);
                        gMtopBuilderSyncRequestMethod = gMtopBuilderClass.getMethod("syncRequest", new Class[0]);
                        gMtopBuilderSetCustomDomainMethod = gMtopBuilderClass.getMethod("setCustomDomain", String.class);
                        gMtopBuilderHeadersMethod = gMtopBuilderClass.getMethod("headers", Map.class);
                        if (hasMtopBusiness) {
                            gMtopBusinessBuildMethod = gMtopBuilderClass.getMethod("build", gMtopClass, gMtopRequestClass, String.class);
                        }
                        gMtopBuilderSetConnectionTimeoutMilliSecondMethod = gMtopBuilderClass.getMethod("setConnectionTimeoutMilliSecond", Integer.TYPE);
                        gMtopBuilderSetSocketTimeoutMilliSecondMethod = gMtopBuilderClass.getMethod("setSocketTimeoutMilliSecond", Integer.TYPE);
                        gMtopResponseIsApiSuccessMethod = gMtopResponseClass.getMethod("isApiSuccess", new Class[0]);
                        gMtopResponseGetDataJsonObjectMethod = gMtopResponseClass.getMethod("getDataJsonObject", new Class[0]);
                        gMtopResponseGetRetCodeMethod = gMtopResponseClass.getMethod("getRetCode", new Class[0]);
                        gMtopResponseGetRetMsgMethod = gMtopResponseClass.getMethod("getRetMsg", new Class[0]);
                        gReflectUtilConvertMapToDataStrMethod = gReflectUtilClass.getMethod("convertMapToDataStr", Map.class);
                        gMtopAvaiableCache = 1;
                    } catch (ClassNotFoundException | NoSuchMethodException unused2) {
                    }
                    gMtopAvaiableCacheChecked = 1;
                }
            }
        }
        return gMtopAvaiableCache;
    }

    /* JADX WARN: Removed duplicated region for block: B:19:0x008f A[Catch: Exception -> 0x01c7, TryCatch #0 {Exception -> 0x01c7, blocks: (B:6:0x0009, B:8:0x002a, B:9:0x002f, B:12:0x0039, B:15:0x0042, B:16:0x0049, B:17:0x0055, B:19:0x008f, B:21:0x00ad, B:23:0x00b3, B:24:0x00bc, B:26:0x00d3, B:27:0x00dc, B:30:0x010c, B:32:0x0126, B:34:0x0142, B:35:0x0145, B:37:0x0156, B:38:0x015c, B:41:0x0169, B:44:0x017b, B:46:0x018f, B:48:0x019b, B:50:0x01b3, B:52:0x009f, B:53:0x004d), top: B:5:0x0009 }] */
    /* JADX WARN: Removed duplicated region for block: B:26:0x00d3 A[Catch: Exception -> 0x01c7, TryCatch #0 {Exception -> 0x01c7, blocks: (B:6:0x0009, B:8:0x002a, B:9:0x002f, B:12:0x0039, B:15:0x0042, B:16:0x0049, B:17:0x0055, B:19:0x008f, B:21:0x00ad, B:23:0x00b3, B:24:0x00bc, B:26:0x00d3, B:27:0x00dc, B:30:0x010c, B:32:0x0126, B:34:0x0142, B:35:0x0145, B:37:0x0156, B:38:0x015c, B:41:0x0169, B:44:0x017b, B:46:0x018f, B:48:0x019b, B:50:0x01b3, B:52:0x009f, B:53:0x004d), top: B:5:0x0009 }] */
    /* JADX WARN: Removed duplicated region for block: B:29:0x010a  */
    /* JADX WARN: Removed duplicated region for block: B:30:0x010c A[Catch: Exception -> 0x01c7, TryCatch #0 {Exception -> 0x01c7, blocks: (B:6:0x0009, B:8:0x002a, B:9:0x002f, B:12:0x0039, B:15:0x0042, B:16:0x0049, B:17:0x0055, B:19:0x008f, B:21:0x00ad, B:23:0x00b3, B:24:0x00bc, B:26:0x00d3, B:27:0x00dc, B:30:0x010c, B:32:0x0126, B:34:0x0142, B:35:0x0145, B:37:0x0156, B:38:0x015c, B:41:0x0169, B:44:0x017b, B:46:0x018f, B:48:0x019b, B:50:0x01b3, B:52:0x009f, B:53:0x004d), top: B:5:0x0009 }] */
    /* JADX WARN: Removed duplicated region for block: B:52:0x009f A[Catch: Exception -> 0x01c7, TryCatch #0 {Exception -> 0x01c7, blocks: (B:6:0x0009, B:8:0x002a, B:9:0x002f, B:12:0x0039, B:15:0x0042, B:16:0x0049, B:17:0x0055, B:19:0x008f, B:21:0x00ad, B:23:0x00b3, B:24:0x00bc, B:26:0x00d3, B:27:0x00dc, B:30:0x010c, B:32:0x0126, B:34:0x0142, B:35:0x0145, B:37:0x0156, B:38:0x015c, B:41:0x0169, B:44:0x017b, B:46:0x018f, B:48:0x019b, B:50:0x01b3, B:52:0x009f, B:53:0x004d), top: B:5:0x0009 }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String postRequest(android.content.Context r8, java.lang.String r9, java.lang.String r10, java.lang.String r11, java.util.Map<java.lang.String, java.lang.String> r12, java.util.Map<java.lang.String, java.lang.String> r13, byte[] r14) {
        /*
            Method dump skipped, instructions count: 456
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.minilibc.android.MtopMethodJniBridge.postRequest(android.content.Context, java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.util.Map, byte[]):java.lang.String");
    }
}
