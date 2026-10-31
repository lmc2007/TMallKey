package com.taobao.wireless.security.sdk.securesignature;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.taobao.wireless.security.sdk.IComponent;
import com.taobao.wireless.security.sdk.SecurityGuardParamContext;

@InterfacePluginInfo(pluginName = "main")
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes13.dex */
public interface ISecureSignatureComponent extends IComponent {
    String signRequest(SecurityGuardParamContext securityGuardParamContext);
}
