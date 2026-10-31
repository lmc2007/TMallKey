package com.alibaba.wireless.security.open.compat;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.alibaba.wireless.security.open.IComponent;
import com.alibaba.wireless.security.open.SecException;

@InterfacePluginInfo(pluginName = "main")
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public interface ICompatComponent extends IComponent {
    String getCachedSecurityToken(int i) throws SecException;
}
