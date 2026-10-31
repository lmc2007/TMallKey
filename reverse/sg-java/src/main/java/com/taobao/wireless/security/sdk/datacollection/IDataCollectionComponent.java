package com.taobao.wireless.security.sdk.datacollection;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.taobao.wireless.security.sdk.IComponent;

@InterfacePluginInfo(pluginName = "main")
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes13.dex */
public interface IDataCollectionComponent extends IComponent {
    @Deprecated
    String getEncryptedDevAndEnvInfo(int i, String str);

    String getNick();

    String getNickEx(int i);

    boolean setNick(String str);

    boolean setNickEx(int i, String str);
}
