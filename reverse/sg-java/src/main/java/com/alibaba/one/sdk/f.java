package com.alibaba.one.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import com.alibaba.one.android.inner.DeviceInfoCapturerFull;
import java.lang.reflect.Field;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public final class f implements Runnable {

    /* renamed from: a, reason: collision with root package name */
    public final /* synthetic */ SharedPreferences f12a;

    public f(SharedPreferences sharedPreferences) {
        this.f12a = sharedPreferences;
    }

    @Override // java.lang.Runnable
    public void run() {
        try {
            Thread.sleep(10000L);
            Class<?> cls = Class.forName("com.alipay.apmobilesecuritysdk.face.APSecuritySdk");
            Object invoke = cls.getDeclaredMethod("getTokenResult", new Class[0]).invoke(cls.getDeclaredMethod("getInstance", Context.class).invoke(cls, DeviceInfoCapturerFull.f5a), new Object[0]);
            Field declaredField = invoke.getClass().getDeclaredField("apdidToken");
            declaredField.setAccessible(true);
            String str = (String) declaredField.get(invoke);
            SharedPreferences.Editor edit = this.f12a.edit();
            edit.putString("2144d8c39b6aea0", str);
            edit.apply();
        } catch (Throwable unused) {
        }
    }
}
