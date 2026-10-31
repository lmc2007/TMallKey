package com.taobao.wireless.security.sdk.dynamicdataencrypt;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.taobao.wireless.security.sdk.IComponent;

@InterfacePluginInfo(pluginName = "main")
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes13.dex */
public interface IDynamicDataEncryptComponent extends IComponent {
    @Deprecated
    String dynamicDecrypt(String str);

    String dynamicDecryptDDp(String str);

    @Deprecated
    String dynamicEncrypt(String str);

    String dynamicEncryptDDp(String str);
}
