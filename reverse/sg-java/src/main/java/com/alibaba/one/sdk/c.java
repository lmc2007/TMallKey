package com.alibaba.one.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import com.alibaba.one.android.inner.DeviceInfoCapturer;
import java.util.concurrent.LinkedBlockingQueue;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public final class c implements Runnable {

    /* renamed from: a, reason: collision with root package name */
    public final /* synthetic */ Context f8a;

    public class a implements ServiceConnection {

        /* renamed from: a, reason: collision with root package name */
        public final /* synthetic */ LinkedBlockingQueue f9a;

        public a(c cVar, LinkedBlockingQueue linkedBlockingQueue) {
            this.f9a = linkedBlockingQueue;
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                this.f9a.put(iBinder);
                DeviceInfoCapturer.a();
            } catch (Exception e) {
                Log.e("bindHwService", "onServiceConnected", e);
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            DeviceInfoCapturer.a();
            this.f9a.clear();
        }
    }

    public c(Context context) {
        this.f8a = context;
    }

    @Override // java.lang.Runnable
    public void run() {
        LinkedBlockingQueue linkedBlockingQueue = new LinkedBlockingQueue(1);
        a aVar = new a(this, linkedBlockingQueue);
        Intent intent = new Intent("com.uodis.opendevice.OPENIDS_SERVICE");
        intent.setPackage("com.huawei.hwid");
        if (!this.f8a.bindService(intent, aVar, 1)) {
            DeviceInfoCapturer.a();
            return;
        }
        Parcel obtain = Parcel.obtain();
        Parcel obtain2 = Parcel.obtain();
        String str = null;
        try {
            try {
                obtain.writeInterfaceToken("com.uodis.opendevice.aidl.OpenDeviceIdentifierService");
                ((IBinder) linkedBlockingQueue.take()).transact(1, obtain, obtain2, 0);
                obtain2.readException();
                str = obtain2.readString();
            } catch (Exception unused) {
                DeviceInfoCapturer.a();
            }
            obtain2.recycle();
            obtain.recycle();
            DeviceInfoCapturer.c = str;
        } catch (Throwable th) {
            obtain2.recycle();
            obtain.recycle();
            throw th;
        }
    }
}
