package com.taobao.wireless.security.adapter.common;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/* renamed from: com.taobao.wireless.security.adapter.common.г, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0010 {

    /* renamed from: а, reason: contains not printable characters */
    private FileChannel f28 = null;

    /* renamed from: б, reason: contains not printable characters */
    private FileLock f29 = null;

    /* renamed from: в, reason: contains not printable characters */
    private RandomAccessFile f30 = null;

    /* renamed from: г, reason: contains not printable characters */
    private File f31 = null;

    /* renamed from: д, reason: contains not printable characters */
    private boolean f32;

    /* renamed from: е, reason: contains not printable characters */
    private String f33;

    /* renamed from: ё, reason: contains not printable characters */
    private Context f34;

    public C0010(Context context, String str) {
        this.f32 = true;
        this.f33 = null;
        this.f34 = null;
        this.f34 = context;
        this.f33 = str;
        this.f32 = m39();
    }

    /* renamed from: в, reason: contains not printable characters */
    private boolean m39() {
        try {
            File filesDir = this.f34.getFilesDir();
            if ((filesDir != null && filesDir.exists()) || ((filesDir = this.f34.getFilesDir()) != null && filesDir.exists())) {
                this.f31 = new File(filesDir.getAbsolutePath() + "/" + this.f33);
                if (!this.f31.exists()) {
                    this.f31.createNewFile();
                }
            }
        } catch (Exception unused) {
            File file = this.f31;
            if (file != null && !file.exists()) {
                try {
                    this.f31.createNewFile();
                } catch (Exception unused2) {
                }
            }
        }
        File file2 = this.f31;
        return file2 != null && file2.exists();
    }

    /* renamed from: а, reason: contains not printable characters */
    public boolean m40() {
        if (!this.f32) {
            this.f32 = m39();
            if (!this.f32) {
                return true;
            }
        }
        try {
            if (this.f31 == null) {
                return false;
            }
            this.f30 = new RandomAccessFile(this.f31, "rw");
            this.f28 = this.f30.getChannel();
            this.f29 = this.f28.lock();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /* renamed from: б, reason: contains not printable characters */
    public boolean m41() {
        boolean z = true;
        if (!this.f32) {
            return true;
        }
        try {
            if (this.f29 != null) {
                this.f29.release();
                this.f29 = null;
            }
        } catch (IOException unused) {
            z = false;
        }
        try {
            if (this.f28 != null) {
                this.f28.close();
                this.f28 = null;
            }
        } catch (IOException unused2) {
            z = false;
        }
        try {
            if (this.f30 == null) {
                return z;
            }
            this.f30.close();
            this.f30 = null;
            return z;
        } catch (IOException unused3) {
            return false;
        }
    }
}
