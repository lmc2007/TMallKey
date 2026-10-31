package com.alibaba.wireless.security.open;

import java.io.PrintStream;
import java.io.PrintWriter;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\classes8.dex */
public class SecException extends Exception {
    public static final int ERROR_NULL_CONTEXT = -100;
    public int a;

    /** 诊断用：native 抛出的最后一个错误（unidbg 会把 pending exception 吞掉，靠这里取）。 */
    public static volatile int lastErrorCode = 0;
    public static volatile String lastMessage = null;

    private static void remember(int code, String msg) {
        lastErrorCode = code;
        lastMessage = msg;
    }

    public SecException(int i) {
        this.a = i;
        remember(i, null);
    }

    public SecException(String str, int i) {
        super(str);
        this.a = i;
        remember(i, str);
    }

    public SecException(String str, Throwable th, int i) {
        super(str, th);
        this.a = i;
        remember(i, str);
    }

    public SecException(Throwable th, int i) {
        super(th);
        this.a = i;
        remember(i, String.valueOf(th));
    }

    public int getErrorCode() {
        return this.a;
    }

    @Override // java.lang.Throwable
    public void printStackTrace(PrintStream printStream) {
        printStream.println("ErrorCode = " + getErrorCode());
        super.printStackTrace(printStream);
    }

    @Override // java.lang.Throwable
    public void printStackTrace(PrintWriter printWriter) {
        printWriter.println("ErrorCode = " + getErrorCode());
        super.printStackTrace(printWriter);
    }

    public void setErrorCode(int i) {
        this.a = i;
    }
}
