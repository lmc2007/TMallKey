package com.alibaba.wireless.security.open.umid;

import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/**
 * 原实现在 app 的 dex 里（APK classes8），这里只补 body 插件初始化要用的最小面：
 * SecurityGuardSecurityBodyPlugin.onPluginLoaded 里调 static a(plugin) 注册 UMID。
 * UMID 只是设备指纹上报，本项目关掉即可。
 */
public class UMIDComponent implements IUMIDComponent {

    private static ISecurityGuardPlugin sPlugin;

    public static void a(ISecurityGuardPlugin plugin) {
        sPlugin = plugin;
    }

    /** body 插件在 objArr[0] & 1 时会调它：异步初始化 UMID。no-op。 */
    public void a() {
    }

    @Override
    public void initUMID(String appKey, int env, String authCode, IUMIDInitListenerEx listener) {
        if (listener != null) {
            listener.onUMIDInitFinishedEx("", 200);
        }
    }

    @Override
    public String getUMIDString(String appKey) {
        return "";
    }

    @Override
    public void setLocalUMID(String umid) {
    }

    @Override
    public void registerInitListener(IUMIDInitListenerEx listener) {
    }

    @Override
    public void unregisterInitListener(IUMIDInitListenerEx listener) {
    }

    @Override
    public int init(ISecurityGuardPlugin plugin, Object... args) {
        sPlugin = plugin;
        return 0;
    }
}
