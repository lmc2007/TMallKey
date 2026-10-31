package com.taobao.wireless.security.sdk.atlasencrypt;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.taobao.wireless.security.sdk.IComponent;

@InterfacePluginInfo(pluginName = "main")
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes13.dex */
public interface IAtlasEncryptComponent extends IComponent {
    String atlasSafeEncrypt(String str);
}
