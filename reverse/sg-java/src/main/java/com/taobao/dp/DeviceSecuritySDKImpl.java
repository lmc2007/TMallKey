package com.taobao.dp;

import android.content.Context;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.alibaba.wireless.security.open.umid.IUMIDInitListenerEx;
import com.taobao.dp.util.CallbackHelper;
import com.taobao.dp.util.a;
import com.taobao.dp.util.b;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public final class DeviceSecuritySDKImpl {
    private static final boolean DEBUG = false;
    private static final int SECURITY_TOKEN_LENGTH = 32;
    private static final String TAG = "DeviceSecuritySDKImpl";
    private static final int UTDID_LENGTH = 24;
    private static DeviceSecuritySDKImpl instance;
    private Context mContext;
    private int mEnv = 0;
    private a mListenerHelper;
    private ISecurityGuardPlugin mPlugin;

    public DeviceSecuritySDKImpl(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.mListenerHelper = null;
        this.mContext = null;
        this.mPlugin = null;
        this.mContext = iSecurityGuardPlugin.getContext();
        this.mPlugin = iSecurityGuardPlugin;
        this.mListenerHelper = new a();
        CallbackHelper.getInstance();
        CallbackHelper.init(this);
    }

    private int getEnvironment(int i) {
        if (i == 0) {
            return 0;
        }
        int i2 = 1;
        if (i != 1) {
            i2 = 2;
            if (i != 2) {
                return this.mEnv;
            }
        }
        return i2;
    }

    private String getSecurityTokenNative(int i, boolean z) {
        return (String) this.mPlugin.getRouter().doCommand(22302, new Object[]{Integer.valueOf(i), Boolean.valueOf(z)});
    }

    private void initUMIDNative(int i) {
        this.mPlugin.getRouter().doCommand(22301, new Object[]{Integer.valueOf(i)});
    }

    private void resetClientDataNative(int i) {
        this.mPlugin.getRouter().doCommand(22304, new Object[]{Integer.valueOf(i)});
    }

    private void setOnlineHostNative(String str) {
        this.mPlugin.getRouter().doCommand(22303, new Object[]{str});
    }

    public a getListenerHelper() {
        return this.mListenerHelper;
    }

    public String getSecurityToken(int i, boolean z) {
        String securityTokenNative = getSecurityTokenNative(i, z);
        return (securityTokenNative == null || "".equals(securityTokenNative) || !(securityTokenNative.length() == SECURITY_TOKEN_LENGTH || securityTokenNative.length() == UTDID_LENGTH)) ? "000000000000000000000000" : securityTokenNative;
    }

    public void init(IUMIDInitListenerEx iUMIDInitListenerEx) {
        int i = this.mEnv;
        this.mListenerHelper.a(i, iUMIDInitListenerEx);
        initUMIDNative(i);
    }

    public void initAsync(int i, IUMIDInitListenerEx iUMIDInitListenerEx) {
        int environment = getEnvironment(i);
        setEnvironment(environment);
        this.mListenerHelper.a(environment, iUMIDInitListenerEx);
        initUMIDNative(environment);
    }

    public int initSync(int i) {
        int environment = getEnvironment(i);
        setEnvironment(environment);
        b bVar = new b(environment);
        this.mListenerHelper.a(environment, bVar);
        initUMIDNative(environment);
        try {
            synchronized (bVar) {
                if (!bVar.b()) {
                    bVar.wait(10000L);
                }
            }
            return bVar.a();
        } catch (Exception unused) {
            return 999;
        }
    }

    public void registerListener(IUMIDInitListenerEx iUMIDInitListenerEx) {
        this.mListenerHelper.a(this.mEnv, iUMIDInitListenerEx);
    }

    public void setEnvironment(int i) {
        if (this.mEnv != i) {
            this.mEnv = i;
        }
    }

    public synchronized void setOnlineHost(String str) {
        if (str != null) {
            if (str.length() != 0) {
                setOnlineHostNative(str);
            }
        }
        throw new IllegalArgumentException("host is null");
    }
}
