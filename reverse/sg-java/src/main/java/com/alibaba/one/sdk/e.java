package com.alibaba.one.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import com.alibaba.one.android.inner.DeviceInfoCapturer;
import java.util.concurrent.LinkedBlockingQueue;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public final class e implements Runnable {

    /* renamed from: a, reason: collision with root package name */
    public final /* synthetic */ Context f10a;

    public class a implements ServiceConnection {

        /* renamed from: a, reason: collision with root package name */
        public final /* synthetic */ LinkedBlockingQueue f11a;

        public a(e eVar, LinkedBlockingQueue linkedBlockingQueue) {
            this.f11a = linkedBlockingQueue;
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                this.f11a.put(iBinder);
                DeviceInfoCapturer.a();
            } catch (Exception e) {
                Log.e("bindOppoService", "onServiceConnected", e);
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            DeviceInfoCapturer.a();
            this.f11a.clear();
        }
    }

    public e(Context context) {
        this.f10a = context;
    }

    /* JADX WARN: Can't wrap try/catch for region: R(13:3|4|5|6|(4:10|11|12|(11:14|(1:16)|17|18|19|20|21|22|(1:24)|25|26))|35|19|20|21|22|(0)|25|26) */
    /* JADX WARN: Code restructure failed: missing block: B:30:0x00a8, code lost:
    
        r0 = move-exception;
     */
    /* JADX WARN: Code restructure failed: missing block: B:31:0x00a9, code lost:
    
        r7.recycle();
        r6.recycle();
     */
    /* JADX WARN: Code restructure failed: missing block: B:32:0x00af, code lost:
    
        throw r0;
     */
    /* JADX WARN: Removed duplicated region for block: B:24:0x00bc  */
    @Override // java.lang.Runnable
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void run() {
        /*
            r11 = this;
            java.util.concurrent.LinkedBlockingQueue r0 = new java.util.concurrent.LinkedBlockingQueue
            r1 = 1
            r0.<init>(r1)
            com.alibaba.one.sdk.e$a r2 = new com.alibaba.one.sdk.e$a
            r2.<init>(r11, r0)
            android.content.Intent r3 = new android.content.Intent
            r3.<init>()
            android.content.ComponentName r4 = new android.content.ComponentName
            java.lang.String r5 = "com.heytap.openid"
            java.lang.String r6 = "com.heytap.openid.IdentifyService"
            r4.<init>(r5, r6)
            r3.setComponent(r4)
            java.lang.String r4 = "action.com.heytap.openid.OPEN_ID_SERVICE"
            r3.setAction(r4)
            android.content.Context r4 = r11.f10a
            boolean r2 = r4.bindService(r3, r2, r1)
            if (r2 == 0) goto Lc1
            android.content.Context r2 = r11.f10a
            java.lang.String r2 = r2.getPackageName()
            com.alibaba.one.android.inner.DeviceInfoCapturer.a()
            r3 = 0
            android.content.Context r4 = r11.f10a     // Catch: android.content.pm.PackageManager.NameNotFoundException -> L42
            android.content.pm.PackageManager r4 = r4.getPackageManager()     // Catch: android.content.pm.PackageManager.NameNotFoundException -> L42
            r5 = 64
            android.content.pm.PackageInfo r4 = r4.getPackageInfo(r2, r5)     // Catch: android.content.pm.PackageManager.NameNotFoundException -> L42
            android.content.pm.Signature[] r4 = r4.signatures     // Catch: android.content.pm.PackageManager.NameNotFoundException -> L42
            goto L43
        L42:
            r4 = r3
        L43:
            r5 = 0
            if (r4 == 0) goto L7e
            int r6 = r4.length
            if (r6 <= 0) goto L7e
            java.lang.String r6 = "SHA1"
            r4 = r4[r5]
            byte[] r4 = r4.toByteArray()
            java.security.MessageDigest r6 = java.security.MessageDigest.getInstance(r6)     // Catch: java.security.NoSuchAlgorithmException -> L7e
            if (r6 == 0) goto L7e
            byte[] r4 = r6.digest(r4)     // Catch: java.security.NoSuchAlgorithmException -> L7e
            java.lang.StringBuilder r6 = new java.lang.StringBuilder     // Catch: java.security.NoSuchAlgorithmException -> L7e
            r6.<init>()     // Catch: java.security.NoSuchAlgorithmException -> L7e
            int r7 = r4.length     // Catch: java.security.NoSuchAlgorithmException -> L7e
            r8 = 0
        L62:
            if (r8 >= r7) goto L79
            r9 = r4[r8]     // Catch: java.security.NoSuchAlgorithmException -> L7e
            r9 = r9 & 255(0xff, float:3.57E-43)
            r9 = r9 | 256(0x100, float:3.59E-43)
            java.lang.String r9 = java.lang.Integer.toHexString(r9)     // Catch: java.security.NoSuchAlgorithmException -> L7e
            r10 = 3
            java.lang.String r9 = r9.substring(r1, r10)     // Catch: java.security.NoSuchAlgorithmException -> L7e
            r6.append(r9)     // Catch: java.security.NoSuchAlgorithmException -> L7e
            int r8 = r8 + 1
            goto L62
        L79:
            java.lang.String r4 = r6.toString()     // Catch: java.security.NoSuchAlgorithmException -> L7e
            goto L7f
        L7e:
            r4 = r3
        L7f:
            android.os.Parcel r6 = android.os.Parcel.obtain()
            android.os.Parcel r7 = android.os.Parcel.obtain()
            java.lang.String r8 = "com.heytap.openid.IOpenID"
            r6.writeInterfaceToken(r8)     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            r6.writeString(r2)     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            r6.writeString(r4)     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            java.lang.String r2 = "OUID"
            r6.writeString(r2)     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            java.lang.Object r0 = r0.take()     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            android.os.IBinder r0 = (android.os.IBinder) r0     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            r0.transact(r1, r6, r7, r5)     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            r7.readException()     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            java.lang.String r3 = r7.readString()     // Catch: java.lang.Throwable -> La8 java.lang.Exception -> Lb0
            goto Lb0
        La8:
            r0 = move-exception
            r7.recycle()
            r6.recycle()
            throw r0
        Lb0:
            r7.recycle()
            r6.recycle()
            boolean r0 = android.text.TextUtils.isEmpty(r3)
            if (r0 == 0) goto Lbe
            java.lang.String r3 = ""
        Lbe:
            com.alibaba.one.android.inner.DeviceInfoCapturer.d = r3
            goto Lc4
        Lc1:
            com.alibaba.one.android.inner.DeviceInfoCapturer.a()
        Lc4:
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.sdk.e.run():void");
    }
}
