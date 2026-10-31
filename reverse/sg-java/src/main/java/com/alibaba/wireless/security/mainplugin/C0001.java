package com.alibaba.wireless.security.mainplugin;

import com.alibaba.wireless.security.framework.IRouterComponent;
import com.taobao.wireless.security.adapter.JNICLibrary;

/* renamed from: com.alibaba.wireless.security.mainplugin.б, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0001 implements IRouterComponent {
    public Object doCommand(int i, Object... objArr) {
        return JNICLibrary.doCommandNative(i, objArr);
    }
}
