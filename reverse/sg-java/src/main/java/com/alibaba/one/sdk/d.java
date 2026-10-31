package com.alibaba.one.sdk;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Pattern;

/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\body\classes.dex */
public class d implements FileFilter {
    @Override // java.io.FileFilter
    public boolean accept(File file) {
        return Pattern.matches("cpu[0-9]+", file.getName());
    }
}
