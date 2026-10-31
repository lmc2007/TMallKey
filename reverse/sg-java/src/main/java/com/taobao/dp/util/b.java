package com.taobao.dp.util;

import com.alibaba.wireless.security.open.umid.IUMIDInitListenerEx;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class b implements IUMIDInitListenerEx {
    private static final String e = "SyncHelper";
    private static final boolean f = false;

    /* renamed from: a, reason: collision with root package name */
    private int f44a;
    private String b;
    private int c = 908;
    private volatile boolean d = false;

    public b(int i) {
        this.f44a = i;
    }

    public int a() {
        return this.c;
    }

    public boolean b() {
        boolean z;
        synchronized (this) {
            z = this.d;
        }
        return z;
    }

    public void onUMIDInitFinishedEx(String str, int i) {
        this.b = str;
        this.c = i;
        synchronized (this) {
            this.d = true;
            notify();
        }
    }
}
