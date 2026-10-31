package com.taobao.dp.util;

import com.alibaba.wireless.security.open.umid.IUMIDInitListenerEx;
import com.taobao.dp.DeviceSecuritySDKImpl;
import java.util.ArrayList;
import java.util.Iterator;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class CallbackHelper {
    private static final boolean DEBUG = false;
    private static final String TAG = "CallbackHelper";
    private static CallbackHelper instance;
    private static DeviceSecuritySDKImpl mDeviceSecurity;

    public static CallbackHelper getInstance() {
        if (instance == null) {
            synchronized (CallbackHelper.class) {
                if (instance == null) {
                    instance = new CallbackHelper();
                }
            }
        }
        return instance;
    }

    public static void init(DeviceSecuritySDKImpl deviceSecuritySDKImpl) {
        mDeviceSecurity = deviceSecuritySDKImpl;
    }

    public void onUpdated(int i, int i2, String str) {
        if (i2 == 0) {
            i2 = 200;
        }
        try {
            ArrayList<IUMIDInitListenerEx> a2 = mDeviceSecurity.getListenerHelper().a(i);
            if (a2 == null) {
                return;
            }
            Iterator<IUMIDInitListenerEx> it = a2.iterator();
            while (it.hasNext()) {
                it.next().onUMIDInitFinishedEx(str, i2);
            }
        } catch (Exception unused) {
        }
    }
}
