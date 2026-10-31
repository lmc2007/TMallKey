package com.alibaba.wireless.security.open.datacollection;

import com.alibaba.wireless.security.framework.InterfacePluginInfo;
import com.alibaba.wireless.security.open.IComponent;

@InterfacePluginInfo(pluginName = "main")
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public interface IDataCollectionComponent extends IComponent {
    String getNick();

    boolean setNick(String str);
}
