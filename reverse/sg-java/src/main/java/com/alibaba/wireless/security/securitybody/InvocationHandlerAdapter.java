package com.alibaba.wireless.security.securitybody;

import android.app.Application;
import android.content.Context;
import com.alibaba.wireless.security.framework.IRouterComponent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class InvocationHandlerAdapter implements InvocationHandler {
    private static IRouterComponent b;
    private static Context c;

    /* renamed from: a, reason: collision with root package name */
    private Object f25a;

    private InvocationHandlerAdapter(Object obj) {
        this.f25a = null;
        this.f25a = obj;
    }

    public static ClassLoader getClassLoader() {
        return InvocationHandlerAdapter.class.getClassLoader();
    }

    public static int init(Context context, IRouterComponent iRouterComponent) {
        if (context != null && iRouterComponent != null && (context instanceof Application)) {
            b = iRouterComponent;
            c = context;
        }
        return 0;
    }

    public static Object newInstance(Class<?> cls, Object obj) {
        return Proxy.newProxyInstance(SecurityBodyAdapter.class.getClassLoader(), new Class[]{cls}, new InvocationHandlerAdapter(obj));
    }

    @Override // java.lang.reflect.InvocationHandler
    public Object invoke(Object obj, Method method, Object[] objArr) {
        Type genericReturnType = method.getGenericReturnType();
        String name = method.getName();
        method.getDeclaringClass().getName();
        Object obj2 = null;
        try {
            obj2 = b.doCommand(20113, new Object[]{this.f25a, obj, method, objArr});
            if (obj2 == null) {
                if ("toString".equals(name)) {
                    return toString();
                }
                if ("hashCode".equals(name)) {
                    return Integer.valueOf(hashCode());
                }
            }
            genericReturnType = method.getGenericReturnType();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (genericReturnType.equals(Integer.TYPE)) {
            if (obj2 instanceof Integer) {
                return obj2;
            }
            return 0;
        }
        if (genericReturnType.equals(Byte.TYPE)) {
            if (obj2 instanceof Byte) {
                return obj2;
            }
            return (byte) 0;
        }
        if (genericReturnType.equals(Short.TYPE)) {
            if (obj2 instanceof Short) {
                return obj2;
            }
            return (short) 0;
        }
        if (genericReturnType.equals(Long.TYPE)) {
            if (obj2 instanceof Long) {
                return obj2;
            }
            return 0L;
        }
        if (genericReturnType.equals(Float.TYPE)) {
            return obj2 instanceof Float ? obj2 : Float.valueOf(0.0f);
        }
        if (genericReturnType.equals(Double.TYPE)) {
            return obj2 instanceof Double ? obj2 : Double.valueOf(0.0d);
        }
        if (genericReturnType.equals(Character.TYPE)) {
            if (obj2 instanceof Character) {
                return obj2;
            }
            return (char) 0;
        }
        if (genericReturnType.equals(Boolean.TYPE)) {
            if (obj2 instanceof Boolean) {
                return obj2;
            }
            return false;
        }
        return obj2;
    }
}
