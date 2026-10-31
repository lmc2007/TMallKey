package com.alibaba.wireless.security.open.litevm;

import com.alibaba.wireless.security.open.litevm.LiteVMParamType;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public class LiteVMParameterWrapper {
    public LiteVMParamType.PARAM_TYPE a;
    public Object b;

    public LiteVMParameterWrapper(LiteVMParamType.PARAM_TYPE param_type, Object obj) {
        this.a = param_type;
        this.b = obj;
    }

    public int getType() {
        return this.a.getValue();
    }

    public Object getValue() {
        return this.b;
    }
}
