package com.alibaba.wireless.security.open.umid;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;

/**
 * 原实现在 app 的 dex 里（APK classes8），这里只补 body 插件初始化要用的最小面：
 * SecurityGuardSecurityBodyPlugin.onPluginLoaded 里 new UMIDComponent(plugin) 注册组件。
 * UMID 只是设备指纹上报，本项目关掉即可 —— 全部返回空/成功。
 */
@InterfacePluginInfo(pluginName = "securitybody")
public class UMIDComponent implements IUMIDComponent {

    private static ISecurityGuardPlugin sPlugin;

    public UMIDComponent(ISecurityGuardPlugin plugin) {
        sPlugin = plugin;
    }

    /** body 插件初始化时（静态）注册 plugin。 */
    public static void a(ISecurityGuardPlugin plugin) {
        sPlugin = plugin;
    }

    /** body 插件在 objArr[0] & 1 时会调它：异步初始化 UMID。no-op。 */
    public void a() {
    }

    @Override
    public int init(ISecurityGuardPlugin plugin, Object... args) {
        sPlugin = plugin;
        return 0;
    }

    @Override
    public String getSecurityToken() {
        return "";
    }

    @Override
    public String getSecurityToken(int i) {
        return "";
    }

    @Override
    public void initUMID() {
    }

    @Override
    public void initUMID(int env, IUMIDInitListenerEx listener) {
        if (listener != null) {
            listener.onUMIDInitFinishedEx("", 200);
        }
    }

    @Override
    public void initUMID(String appKey, int env, String authCode, IUMIDInitListenerEx listener) {
        if (listener != null) {
            listener.onUMIDInitFinishedEx("", 200);
        }
    }

    @Override
    public int initUMIDSync(int env) {
        return 200;
    }

    @Override
    public void registerInitListener(IUMIDInitListener listener) {
    }

    @Override
    public void setEnvironment(int env) {
    }

    @Override
    public void setOnlineHost(String host) {
    }
}
