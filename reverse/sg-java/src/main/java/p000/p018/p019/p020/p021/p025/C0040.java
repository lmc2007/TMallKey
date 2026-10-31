package p000.p018.p019.p020.p021.p025;

import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.initialize.ISecurityGuardPlugin;
import com.taobao.wireless.security.sdk.dynamicdatastore.IDynamicDataStoreComponent;

/* renamed from: а.б.а.а.а.г.а, reason: contains not printable characters */
/* loaded from: C:\Users\64702\Desktop\SRC\TMSCHOOL\.tmp\sgdext\libsgmain\classes.dex */
public class C0040 implements IDynamicDataStoreComponent {

    /* renamed from: а, reason: contains not printable characters */
    private com.alibaba.wireless.security.open.dynamicdatastore.IDynamicDataStoreComponent f105;

    public C0040(ISecurityGuardPlugin iSecurityGuardPlugin) {
        this.f105 = (com.alibaba.wireless.security.open.dynamicdatastore.IDynamicDataStoreComponent) iSecurityGuardPlugin.getInterface(com.alibaba.wireless.security.open.dynamicdatastore.IDynamicDataStoreComponent.class);
    }

    public boolean getBoolean(String str) {
        try {
            return this.f105.getBoolean(str);
        } catch (SecException e) {
            e.printStackTrace();
            return false;
        }
    }

    public byte[] getByteArray(String str) {
        try {
            return this.f105.getByteArray(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getByteArrayDDp(String str) {
        try {
            return this.f105.getByteArrayDDp(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getByteArrayDDpEx(String str, int i) {
        try {
            return this.f105.getByteArrayDDpEx(str, i);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public float getFloat(String str) {
        try {
            return this.f105.getFloat(str);
        } catch (SecException e) {
            e.printStackTrace();
            return -1.0f;
        }
    }

    public int getInt(String str) {
        try {
            return this.f105.getInt(str);
        } catch (SecException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public long getLong(String str) {
        try {
            return this.f105.getLong(str);
        } catch (SecException e) {
            e.printStackTrace();
            return -1L;
        }
    }

    public String getString(String str) {
        try {
            return this.f105.getString(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getStringDDp(String str) {
        try {
            return this.f105.getStringDDp(str);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getStringDDpEx(String str, int i) {
        try {
            return this.f105.getStringDDpEx(str, i);
        } catch (SecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int putBoolean(String str, boolean z) {
        try {
            return this.f105.putBoolean(str, z);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int putByteArray(String str, byte[] bArr) {
        try {
            return this.f105.putByteArray(str, bArr);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int putByteArrayDDp(String str, byte[] bArr) {
        try {
            return this.f105.putByteArrayDDp(str, bArr);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public boolean putByteArrayDDpEx(String str, byte[] bArr, int i) {
        try {
            return this.f105.putByteArrayDDpEx(str, bArr, i);
        } catch (SecException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int putFloat(String str, float f) {
        try {
            return this.f105.putFloat(str, f);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int putInt(String str, int i) {
        try {
            return this.f105.putInt(str, i);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int putLong(String str, long j) {
        try {
            return this.f105.putLong(str, j);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int putString(String str, String str2) {
        try {
            return this.f105.putString(str, str2);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int putStringDDp(String str, String str2) {
        try {
            return this.f105.putStringDDp(str, str2);
        } catch (SecException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public boolean putStringDDpEx(String str, String str2, int i) {
        try {
            return this.f105.putStringDDpEx(str, str2, i);
        } catch (SecException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void removeBoolean(String str) {
        try {
            this.f105.removeBoolean(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeByteArray(String str) {
        try {
            this.f105.removeByteArray(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeByteArrayDDp(String str) {
        try {
            this.f105.removeByteArrayDDp(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeByteArrayDDpEx(String str, int i) {
        try {
            this.f105.removeByteArrayDDpEx(str, i);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeFloat(String str) {
        try {
            this.f105.removeFloat(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeInt(String str) {
        try {
            this.f105.removeInt(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeLong(String str) {
        try {
            this.f105.removeLong(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeString(String str) {
        try {
            this.f105.removeString(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeStringDDp(String str) {
        try {
            this.f105.removeStringDDp(str);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }

    public void removeStringDDpEx(String str, int i) {
        try {
            this.f105.removeStringDDpEx(str, i);
        } catch (SecException e) {
            e.printStackTrace();
        }
    }
}
