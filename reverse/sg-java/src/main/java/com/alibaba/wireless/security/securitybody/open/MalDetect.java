package com.alibaba.wireless.security.securitybody.open;

import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.maldetection.IMalDetect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class MalDetect implements IMalDetect {
    private static ArrayList<IMalDetect.ICallBack> b = new ArrayList<>();
    private static Map<Integer, a> c = new HashMap();

    /* renamed from: a, reason: collision with root package name */
    private ISecurityGuardPlugin f37a;

    static class a {

        /* renamed from: a, reason: collision with root package name */
        public int f38a;
        public String b;
        public String c;
    }

    public MalDetect(ISecurityGuardPlugin iSecurityGuardPlugin) {
        init(iSecurityGuardPlugin, new Object[0]);
    }

    public static void OnDetectionJNI(int i, String str, String str2) {
        ArrayList arrayList;
        synchronized (c) {
            if (c.get(Integer.valueOf(i)) != null) {
                return;
            }
            a aVar = new a();
            aVar.f38a = i;
            aVar.b = str;
            aVar.c = str2;
            c.put(Integer.valueOf(i), aVar);
            synchronized (b) {
                arrayList = (ArrayList) b.clone();
            }
            if (arrayList != null) {
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    ((IMalDetect.ICallBack) it.next()).onDetection(i, str, str2);
                }
            }
        }
    }

    private ArrayList<Map.Entry<Integer, a>> a() {
        ArrayList<Map.Entry<Integer, a>> arrayList = new ArrayList<>();
        synchronized (c) {
            Iterator<Map.Entry<Integer, a>> it = c.entrySet().iterator();
            while (it.hasNext()) {
                arrayList.add(it.next());
            }
        }
        return arrayList;
    }

    public int init(ISecurityGuardPlugin iSecurityGuardPlugin, Object... objArr) {
        this.f37a = iSecurityGuardPlugin;
        return 0;
    }

    public void registerCallBack(IMalDetect.ICallBack iCallBack) {
        synchronized (b) {
            if (iCallBack != null) {
                b.add(iCallBack);
            }
        }
        if (iCallBack != null) {
            Iterator<Map.Entry<Integer, a>> it = a().iterator();
            while (it.hasNext()) {
                a value = it.next().getValue();
                iCallBack.onDetection(value.f38a, value.b, value.c);
            }
        }
    }

    public void unregisterCallBack(IMalDetect.ICallBack iCallBack) {
        synchronized (b) {
            if (iCallBack != null) {
                b.remove(iCallBack);
            }
        }
    }
}
