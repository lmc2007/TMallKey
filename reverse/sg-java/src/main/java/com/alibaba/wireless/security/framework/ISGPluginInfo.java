package com.alibaba.wireless.security.framework;

import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public interface ISGPluginInfo {
    AssetManager getAssetManager();

    ClassLoader getClassLoader();

    PackageInfo getPackageInfo();

    String getPluginName();

    String getPluginPath();

    ISecurityGuardPlugin getSGPluginEntry();

    ISGPluginManager getSGPluginManager();

    String getVersion();
}
