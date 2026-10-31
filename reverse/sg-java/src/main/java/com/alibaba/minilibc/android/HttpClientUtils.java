package com.alibaba.minilibc.android;

import android.os.Build;
import com.alibaba.wireless.security.framework.utils.UserTrackMethodJniBridge;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class HttpClientUtils {
    private static final boolean DEBUG = false;
    private static final int ERROR_HANDLER_METHOD_ID_DOWNLOAD_FILE_FAILED = 100168;
    private static final String TAG = "HttpClientUtils";
    public static final int TIMEOUT_DEFAULT = 22000;
    public static final int TIMEOUT_MIN = 0;

    public static class ResCode {
        public static final int CUSTOM_SSL_HANDSHAKE_ERROR = 1004;
        public static final int CUSTOM_UNKONW_HOST = 1003;
        public static final int EXCEPTION = 1002;
        public static final int HTTP_RESPONSE_NULL = 1000;
        public static final int REQ_PARAM_INVALID = 400;
        public static final int STATUS_OK = 200;
        public static final int UNKNOWN = -1;
    }

    private static void disconnect(URLConnection uRLConnection) {
        if (uRLConnection != null) {
            try {
                if (uRLConnection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) uRLConnection).disconnect();
                } else if (uRLConnection instanceof HttpURLConnection) {
                    ((HttpURLConnection) uRLConnection).disconnect();
                }
            } catch (Throwable unused) {
            }
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:57:0x00c8, code lost:
    
        if (r8 == null) goto L59;
     */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:56:0x00c5 A[Catch: Exception -> 0x00cd, TRY_ENTER, TryCatch #4 {Exception -> 0x00cd, blocks: (B:47:0x00ab, B:48:0x00ca, B:56:0x00c5), top: B:9:0x001b }] */
    /* JADX WARN: Removed duplicated region for block: B:63:0x00da A[Catch: Exception -> 0x00dd, TRY_LEAVE, TryCatch #3 {Exception -> 0x00dd, blocks: (B:69:0x00d5, B:63:0x00da), top: B:68:0x00d5 }] */
    /* JADX WARN: Removed duplicated region for block: B:68:0x00d5 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Type inference failed for: r0v1, types: [java.lang.String] */
    /* JADX WARN: Type inference failed for: r0v11, types: [com.alibaba.minilibc.android.HttpClientResult] */
    /* JADX WARN: Type inference failed for: r0v2 */
    /* JADX WARN: Type inference failed for: r0v3, types: [com.alibaba.minilibc.android.HttpClientResult] */
    /* JADX WARN: Type inference failed for: r0v7, types: [com.alibaba.minilibc.android.HttpClientResult] */
    /* JADX WARN: Type inference failed for: r0v8 */
    /* JADX WARN: Type inference failed for: r4v0, types: [java.util.Map, java.util.Map<java.lang.String, java.lang.String>] */
    /* JADX WARN: Type inference failed for: r4v2, types: [java.io.OutputStream] */
    /* JADX WARN: Type inference failed for: r4v4 */
    /* JADX WARN: Type inference failed for: r4v6 */
    /* JADX WARN: Type inference failed for: r4v7, types: [java.io.OutputStream] */
    /* JADX WARN: Type inference failed for: r4v9, types: [java.io.OutputStream] */
    /* JADX WARN: Type inference failed for: r8v0, types: [int] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static com.alibaba.minilibc.android.HttpClientResult doHttpPost(java.lang.String r3, java.util.Map<java.lang.String, java.lang.String> r4, byte[] r5, java.lang.String r6, int r7, int r8) {
        /*
            Method dump skipped, instructions count: 233
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.minilibc.android.HttpClientUtils.doHttpPost(java.lang.String, java.util.Map, byte[], java.lang.String, int, int):com.alibaba.minilibc.android.HttpClientResult");
    }

    /* JADX WARN: Code restructure failed: missing block: B:46:0x0111, code lost:
    
        if (r6 != null) goto L104;
     */
    /* JADX WARN: Code restructure failed: missing block: B:49:0x0113, code lost:
    
        r6.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:59:0x00ee, code lost:
    
        if (r6 != null) goto L104;
     */
    /* JADX WARN: Code restructure failed: missing block: B:67:0x00cb, code lost:
    
        if (r6 != null) goto L104;
     */
    /* JADX WARN: Not initialized variable reg: 6, insn: 0x0122: IF  (r6 I:??[int, boolean, OBJECT, ARRAY, byte, short, char]) == (0 ??[int, boolean, OBJECT, ARRAY, byte, short, char])  -> B:89:0x0127, block:B:88:0x0122 */
    /* JADX WARN: Removed duplicated region for block: B:52:0x010c A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:60:0x00e9 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:68:0x00c6 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static boolean downloadFile(java.lang.String r18, java.lang.String r19) {
        /*
            Method dump skipped, instructions count: 300
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.minilibc.android.HttpClientUtils.downloadFile(java.lang.String, java.lang.String):boolean");
    }

    private static URLConnection getDefaultURLConnection(String str, int i, int i2, String str2) {
        URLConnection uRLConnection = null;
        try {
        } catch (Throwable th) {
            UserTrackMethodJniBridge.addUtRecord("100168", 1, 0, "", 0L, th.getMessage(), "", "", "", "");
        }
        if (!str.startsWith("http")) {
            return null;
        }
        if (Build.VERSION.SDK_INT < 8) {
            System.setProperty("http.keepAlive", "false");
        }
        uRLConnection = new URL(str).openConnection();
        if (i <= 0) {
            i = TIMEOUT_DEFAULT;
        }
        if (i2 <= 0) {
            i2 = TIMEOUT_DEFAULT;
        }
        if (str.startsWith("https")) {
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) uRLConnection;
            httpsURLConnection.setConnectTimeout(i);
            httpsURLConnection.setReadTimeout(i2);
            httpsURLConnection.setRequestMethod(str2);
        } else {
            HttpURLConnection httpURLConnection = (HttpURLConnection) uRLConnection;
            httpURLConnection.setConnectTimeout(i);
            httpURLConnection.setReadTimeout(i2);
            httpURLConnection.setRequestMethod(str2);
        }
        uRLConnection.setUseCaches(DEBUG);
        if ("POST".equals(str2)) {
            uRLConnection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            uRLConnection.setDoOutput(true);
        }
        uRLConnection.setRequestProperty("Accept-Charset", "UTF-8");
        return uRLConnection;
    }

    private static int getResponseCode(URLConnection uRLConnection) throws IOException {
        return uRLConnection instanceof HttpsURLConnection ? ((HttpsURLConnection) uRLConnection).getResponseCode() : ((HttpURLConnection) uRLConnection).getResponseCode();
    }
}
