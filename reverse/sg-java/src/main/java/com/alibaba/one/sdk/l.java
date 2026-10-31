package com.alibaba.one.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class l {

    /* renamed from: a, reason: collision with root package name */
    public static String f18a = "";

    public static class a implements Runnable {

        /* renamed from: a, reason: collision with root package name */
        public final /* synthetic */ Context f19a;
        public final /* synthetic */ CountDownLatch b;

        public a(Context context, CountDownLatch countDownLatch) {
            this.f19a = context;
            this.b = countDownLatch;
        }

        /* JADX WARN: Code restructure failed: missing block: B:11:0x0068, code lost:
        
            r8.f19a.unbindService(r0);
         */
        /* JADX WARN: Code restructure failed: missing block: B:24:0x0065, code lost:
        
            if (r1 == false) goto L25;
         */
        /* JADX WARN: Code restructure failed: missing block: B:5:0x0054, code lost:
        
            if (r1 != false) goto L30;
         */
        @Override // java.lang.Runnable
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public void run() {
            /*
                r8 = this;
                com.alibaba.one.sdk.l$b r0 = new com.alibaba.one.sdk.l$b
                r0.<init>()
                android.content.Intent r1 = new android.content.Intent
                java.lang.String r2 = "com.google.android.gms.ads.identifier.service.START"
                r1.<init>(r2)
                java.lang.String r2 = "com.google.android.gms"
                r1.setPackage(r2)
                r2 = 0
                android.content.Context r3 = r8.f19a     // Catch: java.lang.Throwable -> L59 java.lang.Exception -> L64
                r4 = 1
                boolean r1 = r3.bindService(r1, r0, r4)     // Catch: java.lang.Throwable -> L59 java.lang.Exception -> L64
                if (r1 == 0) goto L54
                android.content.Context r3 = r8.f19a     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                android.content.pm.PackageManager r3 = r3.getPackageManager()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                java.lang.String r5 = "com.android.vending"
                r3.getPackageInfo(r5, r2)     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                android.os.IBinder r3 = r0.a()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                android.os.Parcel r5 = android.os.Parcel.obtain()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                android.os.Parcel r6 = android.os.Parcel.obtain()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                java.lang.String r7 = "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService"
                r5.writeInterfaceToken(r7)     // Catch: java.lang.Throwable -> L4a
                r3.transact(r4, r5, r6, r2)     // Catch: java.lang.Throwable -> L4a
                r6.readException()     // Catch: java.lang.Throwable -> L4a
                java.lang.String r2 = r6.readString()     // Catch: java.lang.Throwable -> L4a
                r6.recycle()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                r5.recycle()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                com.alibaba.one.sdk.l.f18a = r2     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                goto L54
            L4a:
                r2 = move-exception
                r6.recycle()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                r5.recycle()     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
                throw r2     // Catch: java.lang.Throwable -> L52 java.lang.Exception -> L65
            L52:
                r2 = move-exception
                goto L5c
            L54:
                if (r1 == 0) goto L6b
            L56:
                android.content.Context r1 = r8.f19a     // Catch: java.lang.Exception -> L6b
                goto L68
            L59:
                r1 = move-exception
                r2 = r1
                r1 = 0
            L5c:
                if (r1 == 0) goto L63
                android.content.Context r1 = r8.f19a     // Catch: java.lang.Exception -> L63
                r1.unbindService(r0)     // Catch: java.lang.Exception -> L63
            L63:
                throw r2
            L64:
                r1 = 0
            L65:
                if (r1 == 0) goto L6b
                goto L56
            L68:
                r1.unbindService(r0)     // Catch: java.lang.Exception -> L6b
            L6b:
                java.util.concurrent.CountDownLatch r0 = r8.b
                r0.countDown()
                return
            */
            throw new UnsupportedOperationException("Method not decompiled: com.alibaba.one.sdk.l.a.run():void");
        }
    }

    public static final class b implements ServiceConnection {

        /* renamed from: a, reason: collision with root package name */
        public boolean f20a = false;
        public final LinkedBlockingQueue<IBinder> b = new LinkedBlockingQueue<>(1);

        public IBinder a() {
            if (this.f20a) {
                throw new IllegalStateException();
            }
            this.f20a = true;
            return this.b.take();
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                this.b.put(iBinder);
            } catch (InterruptedException unused) {
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
        }
    }

    public static String a(Context context) {
        if (!TextUtils.isEmpty(f18a)) {
            return f18a;
        }
        if (TextUtils.equals(Build.FINGERPRINT, new String(Base64.decode("RE9PVi9mdWxsX2JpcmRfNjc1MF9jNjZfbS9iaXJkXzY3NTBfYzY2X206Ni4wL01SQTU4Sy8xNDk5NDI5MTMyOnVzZXIvdGVzdC1rZXlzCg==", 0), "UTF-8"))) {
            return f18a;
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        new Thread(new a(context, countDownLatch)).start();
        try {
            countDownLatch.await(50L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException unused) {
        }
        return f18a;
    }
}
