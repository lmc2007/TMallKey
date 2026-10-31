package com.alibaba.wireless.security.securitybody.open;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.securitybody.f;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class c {
    private static Object b = null;
    private static c c = null;
    private static ISecurityGuardPlugin d = null;
    private static final String e = "onPageStart";
    private static final String f = "onPageDestroy";
    private static final String g = "addTouchEvent";
    private static final String h = "addScrollEvent";
    private static final String i = "addKeyEvent";
    private static InvocationHandler j = new a();

    /* renamed from: a, reason: collision with root package name */
    public final String f41a = "PageTrackLog";

    static class a implements InvocationHandler {
        a() {
        }

        @Override // java.lang.reflect.InvocationHandler
        public Object invoke(Object obj, Method method, Object[] objArr) {
            String name = method.getName();
            String str = (String) objArr[0];
            if (name.equals(c.e)) {
                c.c.b(str);
                return null;
            }
            if (name.equals(c.f)) {
                c.c.a(str);
                return null;
            }
            if (name.equals(c.g)) {
                c.c.a(str, (MotionEvent) objArr[1]);
                return null;
            }
            if (name.equals(c.h)) {
                c.c.a(str, ((Integer) objArr[1]).intValue(), ((Integer) objArr[2]).intValue(), ((Integer) objArr[3]).intValue(), ((Integer) objArr[4]).intValue());
                return null;
            }
            if (!name.equals(c.i)) {
                return null;
            }
            c.c.a(str, (KeyEvent) objArr[1]);
            return null;
        }
    }

    public static Object a(Class cls, ISecurityGuardPlugin iSecurityGuardPlugin) {
        if (b == null) {
            synchronized (c.class) {
                if (b == null) {
                    b = com.alibaba.wireless.security.securitybody.f.a(cls, j);
                    c = new c();
                    d = iSecurityGuardPlugin;
                }
            }
        }
        return b;
    }

    void a(String str) {
        Log.d("PageTrackLog", "come into onPageDestroy");
        if (str != null) {
            Log.d("PageTrackLog", "pageID=" + str);
            d.getRouter().doCommand(20401, new Object[]{str, 4});
        }
        Log.d("PageTrackLog", "finish onPageDestroy");
    }

    void a(String str, int i2, int i3, int i4, int i5) {
        Log.d("PageTrackLog", "come into addScrollEvent--Scroll");
        if (str != null) {
            Log.d("PageTrackLog", "pageID=" + str);
            Log.d("PageTrackLog", String.format("after scroll x=%d y=%d, before scroll x=%d y=%d", Integer.valueOf(i2), Integer.valueOf(i3), Integer.valueOf(i4), Integer.valueOf(i5)));
            d.getRouter().doCommand(20401, new Object[]{str, 2, Integer.valueOf(i2), Integer.valueOf(i3), Integer.valueOf(i4), Integer.valueOf(i5)});
        }
        Log.d("PageTrackLog", "finish addScrollEvent--Scroll");
    }

    void a(String str, KeyEvent keyEvent) {
        Log.d("PageTrackLog", "come into addKeyEvent");
        if (str != null) {
            Log.d("PageTrackLog", "pageID=" + str);
            Log.d("PageTrackLog", keyEvent.toString());
            d.getRouter().doCommand(20401, new Object[]{str, 3});
        }
        Log.d("PageTrackLog", "finish addKeyEvent");
    }

    void a(String str, MotionEvent motionEvent) {
        Log.d("PageTrackLog", "come into addTouchEvent--Touch");
        if (str != null) {
            Log.d("PageTrackLog", "pageID=" + str);
            Log.d("PageTrackLog", String.format("x=%d, y=%d, rawX=%d, rawY=%d", Integer.valueOf((int) motionEvent.getX()), Integer.valueOf((int) motionEvent.getY()), Integer.valueOf((int) motionEvent.getRawX()), Integer.valueOf((int) motionEvent.getRawY())));
            d.getRouter().doCommand(20401, new Object[]{str, 1, Integer.valueOf((int) motionEvent.getX()), Integer.valueOf((int) motionEvent.getY()), Integer.valueOf((int) motionEvent.getRawX()), Integer.valueOf((int) motionEvent.getRawY())});
        }
        Log.d("PageTrackLog", "finish addTouchEvent--Touch");
    }

    void b(String str) {
        Log.d("PageTrackLog", "come into onPageStart");
        if (str != null) {
            Log.d("PageTrackLog", "pageID=" + str);
            d.getRouter().doCommand(20401, new Object[]{str, 0});
        }
        Log.d("PageTrackLog", "finish onPageStart");
    }
}
