package com.taobao.wireless.security.adapter.common;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import org.json.JSONObject;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class SPUtility2 {

    /* renamed from: а, reason: contains not printable characters */
    private static Context f22;

    /* renamed from: б, reason: contains not printable characters */
    private static C0010 f23;

    public static String getTempFile(String str) throws IOException {
        String rootDir = rootDir();
        if (rootDir == null) {
            return "";
        }
        File file = new File(rootDir, str);
        return (file.exists() || file.createNewFile()) ? file.getAbsolutePath() : "";
    }

    public static void init(Context context) {
        if (context != null) {
            f22 = context;
            f23 = new C0010(context, "sp.lock");
        }
    }

    public static String read(String str) {
        String str2 = "";
        if (!m27(1)) {
            return "";
        }
        String tempFile;
        try {
            tempFile = getTempFile(str);
        } catch (Exception e) {
            return "";
        }
        synchronized (SPUtility2.class) {
            try {
                if (f23.m40()) {
                    str2 = m24(tempFile, true);
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    f23.m41();
                } catch (Exception ignored2) {
                }
            }
        }
        return str2;
    }

    /* JADX WARN: Code restructure failed: missing block: B:15:0x0026, code lost:
    
        if (r3.length() > 0) goto L18;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String readFromSPUnified(java.lang.String r3, java.lang.String r4, java.lang.String r5) {
        /*
            if (r4 == 0) goto L42
            int r0 = r4.length()
            if (r0 <= 0) goto L42
            if (r3 == 0) goto L42
            int r0 = r3.length()
            if (r0 <= 0) goto L42
            java.lang.Class<com.taobao.wireless.security.adapter.common.SPUtility2> r0 = com.taobao.wireless.security.adapter.common.SPUtility2.class
            monitor-enter(r0)
            com.taobao.wireless.security.adapter.common.г r1 = com.taobao.wireless.security.adapter.common.SPUtility2.f23     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L38
            boolean r1 = r1.m40()     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L38
            r2 = 1
            if (r1 != r2) goto L29
            java.lang.String r3 = m23(r3, r4, r5)     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L38
            if (r3 == 0) goto L29
            int r4 = r3.length()     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L38
            if (r4 <= 0) goto L29
            goto L2a
        L29:
            r3 = r5
        L2a:
            com.taobao.wireless.security.adapter.common.г r4 = com.taobao.wireless.security.adapter.common.SPUtility2.f23     // Catch: java.lang.Throwable -> L3f
            r4.m41()     // Catch: java.lang.Throwable -> L3f
            r5 = r3
            goto L3d
        L31:
            r3 = move-exception
            com.taobao.wireless.security.adapter.common.г r4 = com.taobao.wireless.security.adapter.common.SPUtility2.f23     // Catch: java.lang.Throwable -> L3f
            r4.m41()     // Catch: java.lang.Throwable -> L3f
            throw r3     // Catch: java.lang.Throwable -> L3f
        L38:
            com.taobao.wireless.security.adapter.common.г r3 = com.taobao.wireless.security.adapter.common.SPUtility2.f23     // Catch: java.lang.Throwable -> L3f
            r3.m41()     // Catch: java.lang.Throwable -> L3f
        L3d:
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L3f
            goto L42
        L3f:
            r3 = move-exception
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L3f
            throw r3
        L42:
            return r5
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.common.SPUtility2.readFromSPUnified(java.lang.String, java.lang.String, java.lang.String):java.lang.String");
    }

    public static String readSS(Context context, String str) {
        String str2 = null;
        if (context != null) {
            try {
                if ((Build.VERSION.SDK_INT < 23 || context.getApplicationInfo().targetSdkVersion < 23) && str != null && str.length() > 0) {
                    synchronized (SPUtility2.class) {
                        try {
                            if (f23.m40()) {
                                str2 = Settings.System.getString(context.getContentResolver(), str);
                            }
                        } finally {
                            f23.m41();
                        }
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return str2;
    }

    public static boolean removeFromSPUnified(String str, String str2, boolean z) {
        if (str2 == null || str2.length() <= 0 || str == null || str.length() <= 0) {
            return false;
        }
        synchronized (SPUtility2.class) {
            try {
                if (!f23.m40()) {
                    return false;
                }
                return m28(str, str2);
            } finally {
                f23.m41();
            }
        }
    }

    public static String rootDir() {
        try {
            if (!"mounted".equals(Environment.getExternalStorageState())) {
                return null;
            }
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.com.taobao.dp");
            if (file.exists() || file.mkdir()) {
                return file.getAbsolutePath();
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    public static int saveToFileUnifiedForNative(String str, String str2, String str3, boolean z) {
        int i = 1;
        if (str2 != null && str2.length() > 0 && str != null && str.length() > 0) {
            synchronized (SPUtility2.class) {
                try {
                    if (f23.m40()) {
                        i = m29("SGMANAGER_DATA2", str + "_" + str2, str3);
                    }
                } finally {
                    f23.m41();
                }
            }
        }
        return i;
    }

    public static boolean saveToSPUnified(String str, String str2, String str3, boolean z) {
        return saveToFileUnifiedForNative(str, str2, str3, z) == 0;
    }

    public static void write(String str, String str2) {
        String tempFile;
        try {
            if (m27(0) && (tempFile = getTempFile(str)) != null && tempFile.length() > 0) {
                synchronized (SPUtility2.class) {
                    try {
                        if (f23.m40()) {
                            m26(tempFile, str2, false);
                        }
                    } finally {
                        f23.m41();
                    }
                }
            }
        } catch (IOException unused) {
        }
    }

    public static boolean writeSS(Context context, String str, String str2) {
        boolean z = false;
        if (context != null) {
            try {
                if ((Build.VERSION.SDK_INT < 23 || context.getApplicationInfo().targetSdkVersion < 23) && str != null && str.length() > 0) {
                    synchronized (SPUtility2.class) {
                        try {
                            if (f23.m40()) {
                                z = Settings.System.putString(context.getContentResolver(), str, str2);
                            }
                        } finally {
                            f23.m41();
                        }
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return z;
    }

    /* renamed from: а, reason: contains not printable characters */
    private static int m22(Context context, String str, String str2) {
        OutputStreamWriter outputStreamWriter = null;
        try {
            String absolutePath = context.getFilesDir().getAbsolutePath();
            if (absolutePath != null && absolutePath.length() > 0) {
                File file = new File(absolutePath + File.separator + str + ".tmp");
                StringBuilder sb = new StringBuilder();
                sb.append(absolutePath);
                sb.append(File.separator);
                sb.append(str);
                File file2 = new File(sb.toString());
                OutputStreamWriter outputStreamWriter2 = new OutputStreamWriter(new FileOutputStream(file));
                try {
                    outputStreamWriter2.write(str2);
                    outputStreamWriter2.close();
                    file2.delete();
                    return file.renameTo(file2) ? 0 : 5;
                } catch (Exception unused) {
                    outputStreamWriter = outputStreamWriter2;
                    if (outputStreamWriter == null) {
                        return 6;
                    }
                    try {
                        outputStreamWriter.close();
                        return 6;
                    } catch (Exception unused2) {
                        return 6;
                    }
                } catch (Throwable th) {
                    outputStreamWriter = outputStreamWriter2;
                    th = th;
                    if (outputStreamWriter != null) {
                        try {
                            outputStreamWriter.close();
                        } catch (Exception unused3) {
                        }
                    }
                    throw th;
                }
            }
            return 4;
        } catch (Exception unused4) {
            return 6;
        } catch (Throwable th2) {
            return 6;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    private static String m23(String str, String str2, String str3) {
        if (str2 == null || str2.length() <= 0 || str == null || str.length() <= 0) {
            return str3;
        }
        try {
            String str4 = str + "_" + str2;
            JSONObject m25 = m25("SGMANAGER_DATA2");
            return m25 != null ? m25.getString(str4) : str3;
        } catch (Throwable unused) {
            return str3;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    private static String m24(String str, boolean z) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(str))));
        String readLine = bufferedReader.readLine();
        if (readLine == null) {
            readLine = "";
        }
        sb.append(readLine);
        if (!z) {
            while (true) {
                String readLine2 = bufferedReader.readLine();
                if (readLine2 == null) {
                    break;
                }
                sb.append(readLine2);
            }
        }
        bufferedReader.close();
        return sb.toString();
    }

    /* JADX WARN: Code restructure failed: missing block: B:17:0x005b, code lost:
    
        if (r4 == null) goto L20;
     */
    /* JADX WARN: Code restructure failed: missing block: B:21:0x0056, code lost:
    
        r4.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:25:0x0054, code lost:
    
        if (r4 == null) goto L20;
     */
    /* renamed from: а, reason: contains not printable characters */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private static org.json.JSONObject m25(java.lang.String r4) {
        /*
            r0 = 0
            android.content.Context r1 = com.taobao.wireless.security.adapter.common.SPUtility2.f22     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.io.File r1 = r1.getFilesDir()     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.lang.String r1 = r1.getAbsolutePath()     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.io.File r2 = new java.io.File     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.lang.StringBuilder r3 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r3.<init>()     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r3.append(r1)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.lang.String r1 = java.io.File.separator     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r3.append(r1)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r3.append(r4)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.lang.String r4 = r3.toString()     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r2.<init>(r4)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.io.BufferedReader r4 = new java.io.BufferedReader     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.io.InputStreamReader r1 = new java.io.InputStreamReader     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.io.FileInputStream r3 = new java.io.FileInputStream     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r3.<init>(r2)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r1.<init>(r3)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r4.<init>(r1)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.lang.StringBuilder r1 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L4f java.lang.Exception -> L51
            r1.<init>()     // Catch: java.lang.Throwable -> L4f java.lang.Exception -> L51
        L38:
            java.lang.String r2 = r4.readLine()     // Catch: java.lang.Throwable -> L4f java.lang.Exception -> L51
            if (r2 == 0) goto L42
            r1.append(r2)     // Catch: java.lang.Throwable -> L4f java.lang.Exception -> L51
            goto L38
        L42:
            r4.close()     // Catch: java.lang.Throwable -> L4f java.lang.Exception -> L51
            org.json.JSONObject r4 = new org.json.JSONObject     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            java.lang.String r1 = r1.toString()     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            r4.<init>(r1)     // Catch: java.lang.Throwable -> L53 java.lang.Exception -> L5a
            goto L5f
        L4f:
            goto L54
        L51:
            goto L5b
        L53:
            r4 = r0
        L54:
            if (r4 == 0) goto L5e
        L56:
            r4.close()     // Catch: java.lang.Exception -> L5e
            goto L5e
        L5a:
            r4 = r0
        L5b:
            if (r4 == 0) goto L5e
            goto L56
        L5e:
            r4 = r0
        L5f:
            return r4
        */
        throw new UnsupportedOperationException("Method not decompiled: com.taobao.wireless.security.adapter.common.SPUtility2.m25(java.lang.String):org.json.JSONObject");
    }

    /* renamed from: а, reason: contains not printable characters */
    private static void m26(String str, String str2, boolean z) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(new File(str), z);
        fileOutputStream.write(str2.getBytes());
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    /* renamed from: а, reason: contains not printable characters */
    private static boolean m27(int i) {
        Context context = f22;
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        if (context == null) {
            return false;
        }
        try {
            if (context.getApplicationInfo().targetSdkVersion < 23) {
                return true;
            }
            try {
                Method method = Context.class.getMethod("checkSelfPermission", String.class);
                if (method != null) {
                    return ((Integer) method.invoke(context, i == 0 ? "android.permission.WRITE_EXTERNAL_STORAGE" : "android.permission.READ_EXTERNAL_STORAGE")).intValue() == 0;
                }
            } catch (Throwable unused) {
            }
            return false;
        } catch (Throwable unused2) {
            return true;
        }
    }

    /* renamed from: а, reason: contains not printable characters */
    private static boolean m28(String str, String str2) {
        Context context = f22;
        if (context == null || str2 == null || str2.length() <= 0 || str == null || str.length() <= 0) {
            return false;
        }
        try {
            String str3 = str + "_" + str2;
            JSONObject m25 = m25("SGMANAGER_DATA2");
            if (m25 == null) {
                return false;
            }
            m25.remove(str3);
            return m22(context, "SGMANAGER_DATA2", m25.toString()) == 0;
        } catch (Throwable unused) {
            return false;
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    private static int m29(String str, String str2, String str3) {
        Context context = f22;
        if (str2 != null && str2.length() > 0 && str != null && str.length() > 0) {
            try {
                JSONObject m25 = m25(str);
                if (m25 == null) {
                    m25 = new JSONObject();
                }
                m25.put(str2, str3);
                return m22(context, str, m25.toString());
            } catch (Throwable unused) {
            }
        }
        return 2;
    }
}
