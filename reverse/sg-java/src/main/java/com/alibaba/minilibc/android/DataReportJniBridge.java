package com.alibaba.minilibc.android;

import android.content.Context;
import com.taobao.wireless.security.adapter.datareport.C0017;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class DataReportJniBridge {
    private static final boolean DEBUG = false;
    private static final String TAG = "DataReportJniBridge";
    private static Class gAccsAdapterClass;
    private static volatile int gAccsAvaiableCache;
    private static volatile int gAccsAvaiableCacheChecked;
    private static Class gOrangeAdapterClass;
    private static volatile int gOrangeAvailableCache;
    private static volatile int gOrangeAvailableCacheChecked;
    private static Method gOrangeRegisterListenerMethod;
    private static Method gRegisterListenerMethod;

    public static int mtopAvaiableBridge() {
        return MtopMethodJniBridge.mtopAvaiable();
    }

    public static String sendReportBridge(String str, String str2, byte[] bArr) {
        HashMap hashMap;
        if (ConditionTool.stringNotNullOrEmpty(str2)) {
            hashMap = new HashMap();
            hashMap.put("keyindex", str2);
        } else {
            hashMap = null;
        }
        HttpClientResult doHttpPost = HttpClientUtils.doHttpPost(str, hashMap, bArr, null, 11000, 11000);
        StringBuilder sb = new StringBuilder();
        sb.append(doHttpPost.mResponseCode);
        sb.append("|");
        sb.append(doHttpPost.mResponseCode == 200 ? doHttpPost.mString : doHttpPost.mExceptionString);
        return sb.toString();
    }

    public static String sendReportBridgeHttps(String str, Map<String, String> map, byte[] bArr) {
        C0017.C0020 c0020 = new C0017.C0020();
        HashMap hashMap = new HashMap();
        hashMap.put("keyindex", "wb_sc_int_res_k1");
        if (map != null) {
            hashMap.putAll(map);
        }
        long currentTimeMillis = System.currentTimeMillis();
        HttpClientResult doHttpPost = HttpClientUtils.doHttpPost(str, hashMap, bArr, null, 11000, 11000);
        long currentTimeMillis2 = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append(doHttpPost.mResponseCode);
        sb.append("|");
        sb.append(doHttpPost.mResponseCode == 200 ? doHttpPost.mString : doHttpPost.mExceptionString);
        if (C0017.m79()) {
            c0020.m88(str);
            c0020.m81(bArr != null ? bArr.length : 0);
            c0020.m83(C0017.EnumC0021.https);
            c0020.m86(doHttpPost.mResponseCode);
            String str2 = doHttpPost.mString;
            c0020.m90(str2 != null ? str2.length() : 0);
            c0020.m82(currentTimeMillis2 - currentTimeMillis);
            c0020.m87(currentTimeMillis);
            C0017.m76(c0020);
        }
        return sb.toString();
    }

    public static String sendReportBridgeHttps(String str, byte[] bArr) {
        return sendReportBridgeHttps(str, null, bArr);
    }

    public static String sendReportBridgeMtop(Context context, String str, String str2, String str3, Map<String, String> map, Map<String, String> map2, byte[] bArr) {
        return MtopMethodJniBridge.postRequest(context, str, str2, str3, map, map2, bArr);
    }
}
