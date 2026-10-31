package com.alibaba.wireless.security.open.litevm;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public class LiteVMInstance {
    public Object a;
    public String b;
    public String c;

    public LiteVMInstance(Object obj, String str, String str2) {
        this.b = "";
        this.c = "";
        this.b = str;
        this.c = str2;
        this.a = obj;
    }

    public String getAuthCode() {
        return this.b;
    }

    public String getBizId() {
        return this.c;
    }

    public Object getImpl() {
        return this.a;
    }
}
