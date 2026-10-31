package com.alibaba.wireless.security.securitybody;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import com.alibaba.wireless.security.framework.IRouterComponent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class LifeCycle {
    public static final int APP_BACKGROUND = 0;
    public static final int APP_FOREGROUND = 1;
    public static final int APP_IGNORE = 2;

    /* renamed from: a, reason: collision with root package name */
    private static final String f26a = "LifeCycle";
    private static boolean b = false;
    private static Handler c = null;
    private static IRouterComponent d = null;
    private static ActivityManager e = null;
    private static Context f = null;
    private static boolean g = false;
    private static volatile Activity h;
    private static final View.AccessibilityDelegate i = new c();
    private static final List<Activity> j = new ArrayList();
    private static WeakReference<Activity> k = new WeakReference<>(null);
    private static WeakReference<Activity> l = new WeakReference<>(null);
    private static int m = -1;
    private static final Handler n = new Handler(Looper.getMainLooper());
    private static volatile boolean o = false;

    static class a implements Runnable {

        /* renamed from: a, reason: collision with root package name */
        final /* synthetic */ int f27a;
        final /* synthetic */ int b;

        a(int i, int i2) {
            this.f27a = i;
            this.b = i2;
        }

        @Override // java.lang.Runnable
        public void run() {
            int i = this.f27a;
            if (i == 2 || ((i != 1 || LifeCycle.b(LifeCycle.f)) && !(this.f27a == 0 && LifeCycle.b(LifeCycle.f)))) {
                if (LifeCycle.b) {
                    Log.i(LifeCycle.f26a, "notifyCurrentStatus( " + this.b + " )");
                }
                if (LifeCycle.d == null) {
                    return;
                }
                LifeCycle.d.doCommand(20109, new Object[]{Integer.valueOf(this.b)});
                return;
            }
            if (LifeCycle.b) {
                Log.i(LifeCycle.f26a, "notifyCurrentStatus return ( " + this.b + " " + this.f27a + " )");
            }
        }
    }

    static class b implements Application.ActivityLifecycleCallbacks {
        b() {
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivityCreated(Activity activity, Bundle bundle) {
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivityDestroyed(Activity activity) {
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivityPaused(Activity activity) {
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivityResumed(Activity activity) {
            activity.getClass().getName();
            Activity unused = LifeCycle.h = activity;
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivityStarted(Activity activity) {
        }

        @Override // android.app.Application.ActivityLifecycleCallbacks
        public void onActivityStopped(Activity activity) {
            Activity unused = LifeCycle.h = null;
        }
    }

    static class c extends View.AccessibilityDelegate {
        c() {
        }

        @Override // android.view.View.AccessibilityDelegate
        public boolean performAccessibilityAction(View view, int i, Bundle bundle) {
            if (LifeCycle.d != null) {
                try {
                    LifeCycle.d.doCommand(20112, new Object[]{Integer.valueOf(i)});
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return super.performAccessibilityAction(view, i, bundle);
        }
    }

    static class d implements Runnable {
        d() {
        }

        @Override // java.lang.Runnable
        public void run() {
            int i;
            boolean unused = LifeCycle.o = false;
            if (LifeCycle.l.get() != LifeCycle.k.get()) {
                boolean unused2 = LifeCycle.o = true;
                WeakReference unused3 = LifeCycle.l = new WeakReference(LifeCycle.k.get());
                i = LifeCycle.i();
            } else {
                i = LifeCycle.i();
                if (LifeCycle.m == i) {
                    return;
                } else {
                    boolean unused4 = LifeCycle.o = true;
                }
            }
            int unused5 = LifeCycle.m = i;
        }
    }

    private static void a(View view) {
        if ((view instanceof Button) || (view instanceof EditText) || view.isClickable() || view.isLongClickable()) {
            view.setAccessibilityDelegate(i);
        }
    }

    private static int b(View view) {
        if (view == null) {
            return 0;
        }
        if (!(view instanceof ViewGroup)) {
            return 1;
        }
        LinkedList linkedList = new LinkedList();
        linkedList.add((ViewGroup) view);
        int i2 = 0;
        while (!linkedList.isEmpty()) {
            ViewGroup viewGroup = (ViewGroup) linkedList.removeFirst();
            a(viewGroup);
            int i3 = i2 + 1;
            for (int i4 = 0; i4 < viewGroup.getChildCount(); i4++) {
                View childAt = viewGroup.getChildAt(i4);
                if (childAt instanceof ViewGroup) {
                    linkedList.addLast((ViewGroup) childAt);
                } else {
                    a(childAt);
                    i3++;
                }
            }
            i2 = i3;
        }
        return i2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean b(Context context) {
        boolean z = false;
        try {
            ActivityManager activityManager = e;
            if (activityManager != null) {
                String packageName = context.getPackageName();
                List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(1);
                if (runningTasks != null && runningTasks.size() > 0) {
                    ComponentName componentName = runningTasks.get(0).topActivity;
                    if (packageName.equals(componentName.getPackageName())) {
                        z = context.getPackageManager().getActivityInfo(componentName, 0).processName.equals(context.getApplicationInfo().processName);
                    }
                }
            }
        } catch (Exception e2) {
            if (b) {
                Log.e(f26a, e2.toString());
            }
        }
        if (b) {
            Log.i(f26a, "isAppOnTopActivity() = " + z);
        }
        return z;
    }

    public static Activity getCurrentActivity() {
        return h;
    }

    private static void h() {
        HandlerThread handlerThread = new HandlerThread("SGActivityStatus");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        if (looper != null) {
            c = new Handler(looper);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int i() {
        View decorView;
        if (k.get() == null || k.get().getWindow() == null || (decorView = k.get().getWindow().getDecorView()) == null || !(decorView instanceof ViewGroup)) {
            return -1;
        }
        LinkedList linkedList = new LinkedList();
        linkedList.add((ViewGroup) decorView);
        StringBuilder sb = new StringBuilder();
        int i2 = 0;
        while (!linkedList.isEmpty()) {
            ViewGroup viewGroup = (ViewGroup) linkedList.removeFirst();
            int i3 = i2;
            int i4 = 0;
            while (true) {
                if (i4 < viewGroup.getChildCount()) {
                    View childAt = viewGroup.getChildAt(i4);
                    if (childAt instanceof ViewGroup) {
                        linkedList.addLast((ViewGroup) childAt);
                    } else {
                        sb.append(childAt.hashCode());
                        i3++;
                        if (i3 >= 150) {
                            linkedList.clear();
                            break;
                        }
                    }
                    i4++;
                }
            }
            i2 = i3;
        }
        return sb.toString().hashCode();
    }

    public static int init(Context context, IRouterComponent iRouterComponent) {
        if (b) {
            Log.i(f26a, "call lifecycle init begin");
        }
        if (context != null && iRouterComponent != null && (context instanceof Application)) {
            f = context;
            d = iRouterComponent;
            h();
            if (c == null) {
                return 0;
            }
            e = (ActivityManager) context.getSystemService("activity");
        }
        return 0;
    }

    public static boolean isViewChanged() {
        boolean z = o;
        n.post(new d());
        return z;
    }

    public static int notifyCurrentStatus(int i2, int i3) {
        Handler handler = c;
        if (handler == null) {
            return 0;
        }
        handler.post(new a(i3, i2));
        return 0;
    }

    public static void onActivityDestroy(Activity activity) {
        if (Looper.myLooper() == Looper.getMainLooper() && activity != null) {
            j.remove(activity);
        }
    }

    public static void onActivityStart(Activity activity, int i2) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        if (i2 != 0 && activity != null && activity.getWindow() != null && !j.contains(activity)) {
            j.add(activity);
            View decorView = activity.getWindow().getDecorView();
            if (decorView == null) {
                return;
            } else {
                b(decorView);
            }
        }
        k = new WeakReference<>(activity);
    }

    public static void onActivityStop(Activity activity) {
        if (Looper.myLooper() == Looper.getMainLooper() && k.get() == activity) {
            k = new WeakReference<>(null);
        }
    }

    public static void registerCallBack() {
        Context context;
        if (Build.VERSION.SDK_INT >= 16 && (context = f) != null && (context instanceof Application)) {
            ((Application) context).registerActivityLifecycleCallbacks(new b());
        }
    }
}
